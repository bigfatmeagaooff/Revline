package com.revline.tracker.util

import com.revline.tracker.data.TrackPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
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
}
