package com.revline.tracker.util

import com.revline.tracker.data.GForcePoint
import com.revline.tracker.data.TrackPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure functions for deriving trip stats from a list of [TrackPoint]s.
 * No Android dependencies — easy to unit test and reuse server-side later.
 *
 * GPS outlier rejection (Phase 2): in weak-reception areas the provider occasionally
 * returns a wildly inaccurate fix, which — because speed is distance/time between
 * points — produces phantom speed spikes (a real test drive logged 402 km/h in a CRV).
 * Raw points are kept as-recorded in the DB; we clean at calculation/render time via:
 *   1. accuracy filtering — drop points whose reported accuracy is worse than
 *      [MAX_ACCURACY_METERS] (points with unknown accuracy, e.g. legacy V1.0 data,
 *      are kept),
 *   2. a speed sanity ceiling — reject a segment whose *derived* speed exceeds
 *      [MAX_PLAUSIBLE_KMH], bridging across the dropped point to the next good one so
 *      total distance isn't under-counted.
 * [distanceKm], [topSpeedKmh] and the route-map coloring all build on [cleanSegments]
 * so they share the same cleaned data.
 */
object SpeedCalculator {

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val MPS_TO_KMH = 3.6f

    /** Accuracy radius (m) beyond which a fix is treated as low-confidence. */
    const val MAX_ACCURACY_METERS = 30f

    /** Generous physical speed ceiling (km/h); segments above this are GPS jumps. */
    const val MAX_PLAUSIBLE_KMH = 250f

    /** Below this interpolated GPS speed, a G reading is treated as phone-handling noise. */
    const val MOVING_THRESHOLD_KMH = 5f

    /** A G reading with no TrackPoint within this window is treated as stationary. */
    private const val MAX_INTERP_GAP_MS = 5_000L

    /** A cleaned, kept segment between two confident points, with a speed for coloring. */
    data class Segment(
        val startLat: Double,
        val startLon: Double,
        val endLat: Double,
        val endLon: Double,
        val startTime: Long,
        val endTime: Long,
        val distanceMeters: Double,
        val speedKmh: Float
    ) {
        val durationMillis: Long get() = endTime - startTime
    }

    /** Great-circle distance between two coordinates, in meters. */
    fun haversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /** A point is confident if its accuracy is unknown or within [MAX_ACCURACY_METERS]. */
    private fun isConfident(point: TrackPoint): Boolean {
        val accuracy = point.accuracyMeters ?: return true
        return accuracy.isFinite() && accuracy <= MAX_ACCURACY_METERS
    }

    /** Speed derived purely from distance/time between two points, in km/h. */
    private fun derivedSpeedKmh(a: TrackPoint, b: TrackPoint): Float {
        val seconds = (b.timestamp - a.timestamp) / 1000.0
        if (seconds <= 0.0) return 0f
        val meters = haversineMeters(a.lat, a.lon, b.lat, b.lon)
        return ((meters / seconds) * MPS_TO_KMH).toFloat()
    }

    /** Representative speed for a kept segment: prefer raw provider speed, else derived. */
    private fun segmentSpeedKmh(a: TrackPoint, b: TrackPoint): Float {
        val raws = listOfNotNull(a.speedMps, b.speedMps).filter { it.isFinite() && it >= 0f }
        return if (raws.isNotEmpty()) raws.max() * MPS_TO_KMH else derivedSpeedKmh(a, b)
    }

    /**
     * Builds the cleaned list of segments: accuracy-passing points, with single-point
     * speed spikes dropped and bridged. Each kept segment carries its haversine distance
     * and a representative speed (raw-preferred) for stats and route coloring.
     */
    fun cleanSegments(points: List<TrackPoint>): List<Segment> {
        val good = points.filter { isConfident(it) }
        if (good.size < 2) return emptyList()

        val segments = ArrayList<Segment>(good.size)
        var prev = good[0]
        for (i in 1 until good.size) {
            val cur = good[i]
            // Reject a positional jump; keep `prev` so we bridge to the next good point.
            if (derivedSpeedKmh(prev, cur) > MAX_PLAUSIBLE_KMH) continue

            segments.add(
                Segment(
                    startLat = prev.lat,
                    startLon = prev.lon,
                    endLat = cur.lat,
                    endLon = cur.lon,
                    startTime = prev.timestamp,
                    endTime = cur.timestamp,
                    distanceMeters = haversineMeters(prev.lat, prev.lon, cur.lat, cur.lon),
                    speedKmh = segmentSpeedKmh(prev, cur)
                )
            )
            prev = cur
        }
        return segments
    }

    /** Total distance of the cleaned trail in kilometers. */
    fun distanceKm(points: List<TrackPoint>): Float {
        val meters = cleanSegments(points).sumOf { it.distanceMeters }
        return (meters / 1000.0).toFloat()
    }

    /** Average speed in km/h given a distance and a duration. */
    fun avgSpeedKmh(distanceKm: Float, durationMillis: Long): Float {
        if (durationMillis <= 0L) return 0f
        val hours = durationMillis / 3_600_000.0
        if (hours <= 0.0) return 0f
        return (distanceKm / hours).toFloat()
    }

    /** Top speed in km/h across the cleaned segments (raw-preferred, spikes excluded). */
    fun topSpeedKmh(points: List<TrackPoint>): Float {
        var top = 0f
        for (segment in cleanSegments(points)) {
            if (segment.speedKmh.isFinite()) top = max(top, segment.speedKmh)
        }
        return top
    }

    fun mpsToKmh(mps: Float): Float = mps * MPS_TO_KMH

    // --- Speed-gated G-force (Phase 3.3 Feature 1) ---

    private data class SpeedSample(val timestamp: Long, val speedKmh: Float)

    /** Per-TrackPoint speed (raw provider preferred, else derived from the previous point). */
    private fun speedSamples(points: List<TrackPoint>): List<SpeedSample> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<SpeedSample>(points.size)
        for (i in points.indices) {
            val p = points[i]
            val raw = p.speedMps
            val kmh = when {
                raw != null && raw.isFinite() && raw >= 0f -> raw * MPS_TO_KMH
                i > 0 -> {
                    val a = points[i - 1]
                    val seconds = (p.timestamp - a.timestamp) / 1000.0
                    if (seconds > 0.0) {
                        ((haversineMeters(a.lat, a.lon, p.lat, p.lon) / seconds) * MPS_TO_KMH).toFloat()
                    } else 0f
                }
                else -> 0f
            }
            out.add(SpeedSample(p.timestamp, if (kmh.isFinite() && kmh >= 0f) kmh else 0f))
        }
        return out
    }

    /** Interpolated GPS speed (km/h) at [timestamp], or null if no fix within the window. */
    private fun speedKmhAt(samples: List<SpeedSample>, timestamp: Long): Float? {
        if (samples.isEmpty()) return null
        var before: SpeedSample? = null
        var after: SpeedSample? = null
        for (s in samples) {
            if (s.timestamp <= timestamp) before = s
            if (s.timestamp >= timestamp) { after = s; break }
        }
        return when {
            before != null && after != null -> {
                if (before.timestamp == after.timestamp) {
                    before.speedKmh
                } else {
                    val nearest = min(timestamp - before.timestamp, after.timestamp - timestamp)
                    if (nearest > MAX_INTERP_GAP_MS) null
                    else {
                        val frac = (timestamp - before.timestamp).toFloat() /
                            (after.timestamp - before.timestamp).toFloat()
                        before.speedKmh + (after.speedKmh - before.speedKmh) * frac
                    }
                }
            }
            before != null -> if (timestamp - before.timestamp <= MAX_INTERP_GAP_MS) before.speedKmh else null
            after != null -> if (after.timestamp - timestamp <= MAX_INTERP_GAP_MS) after.speedKmh else null
            else -> null
        }
    }

    /**
     * Filters [gForcePoints] down to those captured while the car was actually moving
     * (interpolated GPS speed ≥ [MOVING_THRESHOLD_KMH]) — removes phone-handling spikes at
     * trip start/end. Applied at calculation time; raw points stay in the DB. If there's no
     * GPS to verify movement, all readings are treated as stationary and excluded.
     */
    fun movingGForcePoints(
        trackPoints: List<TrackPoint>,
        gForcePoints: List<GForcePoint>
    ): List<GForcePoint> {
        if (gForcePoints.isEmpty()) return emptyList()
        val samples = speedSamples(trackPoints)
        if (samples.isEmpty()) return emptyList()
        return gForcePoints.filter { gp ->
            val speed = speedKmhAt(samples, gp.timestamp)
            speed != null && speed >= MOVING_THRESHOLD_KMH
        }
    }
}
