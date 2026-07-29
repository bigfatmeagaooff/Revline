package com.revline.tracker.util

import com.revline.tracker.data.TrackPoint

/**
 * Derives the enhanced post-trip stats (idle time, 0–100 / 0–60, longest fast
 * stretch, moving average) from cleaned [TrackPoint] data. Pure Kotlin, computed on
 * read — nothing is denormalized onto the Trip row.
 *
 * Everything here builds on [SpeedCalculator.cleanSegments], so it inherits the same
 * GPS outlier rejection (bad fixes can't fake a sub-second 0–100 or a phantom stretch).
 */
object TripStatsCalculator {

    /** Speeds at/under this are treated as "stopped" (absorbs GPS jitter at standstill). */
    private const val IDLE_THRESHOLD_KMH = 2f

    /** A launch must dip to/under this to (re)arm a 0–X measurement. */
    private const val LAUNCH_THRESHOLD_KMH = 5f

    /** Default threshold for the "longest stretch above" stat. */
    const val FAST_STRETCH_THRESHOLD_KMH = 100

    /** A stop only counts once the car has actually been driving (above this). */
    private const val STOP_ARM_THRESHOLD_KMH = 10f

    /** At/under this the car is considered stopped for stop-counting. */
    private const val STOP_THRESHOLD_KMH = 2f

    /** A stop must last this long to count (filters GPS jitter at low speed). */
    private const val MIN_STOP_MILLIS = 3_000L

    /** Altitude deltas smaller than this are GPS noise, not real elevation change. */
    private const val MIN_ELEVATION_DELTA_M = 1.0

    /** A single jump bigger than this is a bad fix, not a real climb. */
    private const val MAX_ELEVATION_DELTA_M = 30.0

    /** Below this many usable altitude readings, elevation stats aren't trustworthy. */
    private const val MIN_ALTITUDE_SAMPLES = 10

    data class Stats(
        val idleMillis: Long,
        val movingAvgKmh: Float?,
        val zeroToHundredSec: Float?,
        val zeroToSixtySec: Float?,
        val longestStretchKm: Float,
        val longestStretchThresholdKmh: Int,
        /** Discrete stop events (traffic lights, junctions) during the drive. */
        val stopCount: Int,
        /** Total metres climbed, or null when this trip has no reliable altitude data. */
        val elevationGainM: Float?,
        /** Total metres descended, or null when this trip has no reliable altitude data. */
        val elevationLossM: Float?
    )

    fun compute(
        points: List<TrackPoint>,
        totalDurationMillis: Long,
        distanceKm: Float
    ): Stats {
        val segments = SpeedCalculator.cleanSegments(points)

        val idleMillis = segments
            .filter { it.speedKmh < IDLE_THRESHOLD_KMH }
            .sumOf { it.durationMillis }

        val movingMillis = totalDurationMillis - idleMillis
        val movingAvgKmh = if (movingMillis > 0L && distanceKm > 0f) {
            (distanceKm / (movingMillis / 3_600_000.0)).toFloat()
        } else null

        val elevation = elevation(points)

        return Stats(
            idleMillis = idleMillis,
            movingAvgKmh = movingAvgKmh,
            zeroToHundredSec = fastestLaunchSeconds(segments, 100f),
            zeroToSixtySec = fastestLaunchSeconds(segments, 60f),
            longestStretchKm = longestStretchKm(segments, FAST_STRETCH_THRESHOLD_KMH.toFloat()),
            longestStretchThresholdKmh = FAST_STRETCH_THRESHOLD_KMH,
            stopCount = stopCount(segments),
            elevationGainM = elevation?.first,
            elevationLossM = elevation?.second
        )
    }

    /**
     * Counts discrete stop events: speed drops to a standstill for at least
     * [MIN_STOP_MILLIS] after having been genuinely driving. Re-arms only once the car
     * gets moving again, so one long wait at a light counts once, not once per segment.
     */
    private fun stopCount(segments: List<SpeedCalculator.Segment>): Int {
        var count = 0
        var armed = false        // has been driving since the last counted stop
        var stoppedSince: Long? = null
        var countedThisStop = false

        for (segment in segments) {
            val speed = segment.speedKmh
            when {
                speed >= STOP_ARM_THRESHOLD_KMH -> {
                    armed = true
                    stoppedSince = null
                    countedThisStop = false
                }
                speed <= STOP_THRESHOLD_KMH -> {
                    if (stoppedSince == null) stoppedSince = segment.startTime
                    if (armed && !countedThisStop &&
                        segment.endTime - stoppedSince!! >= MIN_STOP_MILLIS
                    ) {
                        count++
                        countedThisStop = true
                        armed = false
                    }
                }
                // Between the two thresholds: crawling. Neither a stop nor a re-arm.
            }
        }
        return count
    }

    /**
     * Elevation gain/loss in metres, or null when the trip lacks trustworthy altitude
     * data. Uses only accuracy-passing points and ignores deltas that are either too
     * small to be real (noise) or too large to be plausible (bad fix).
     */
    private fun elevation(points: List<TrackPoint>): Pair<Float, Float>? {
        val altitudes = points
            .filter { p ->
                val acc = p.accuracyMeters
                p.altitude != null && (acc == null || (acc.isFinite() && acc <= SpeedCalculator.MAX_ACCURACY_METERS))
            }
            .mapNotNull { it.altitude }
        if (altitudes.size < MIN_ALTITUDE_SAMPLES) return null

        var gain = 0.0
        var loss = 0.0
        for (i in 1 until altitudes.size) {
            val delta = altitudes[i] - altitudes[i - 1]
            val magnitude = kotlin.math.abs(delta)
            if (magnitude < MIN_ELEVATION_DELTA_M || magnitude > MAX_ELEVATION_DELTA_M) continue
            if (delta > 0) gain += delta else loss += -delta
        }
        return gain.toFloat() to loss.toFloat()
    }

    /**
     * Fastest clean run from near-0 up through [targetKmh], in seconds, or null if the
     * trip never gets there from a standstill. Re-arms each time speed drops back down.
     */
    private fun fastestLaunchSeconds(
        segments: List<SpeedCalculator.Segment>,
        targetKmh: Float
    ): Float? {
        var launchStart: Long? = null
        var best: Long? = null
        for (segment in segments) {
            val speed = segment.speedKmh
            when {
                speed <= LAUNCH_THRESHOLD_KMH -> launchStart = segment.startTime
                speed >= targetKmh && launchStart != null -> {
                    val dt = segment.endTime - launchStart!!
                    if (dt > 0L && (best == null || dt < best!!)) best = dt
                    launchStart = null // require returning to a standstill before re-measuring
                }
            }
        }
        return best?.let { it / 1000f }
    }

    /** Distance (km) of the longest continuous run of segments above [thresholdKmh]. */
    private fun longestStretchKm(
        segments: List<SpeedCalculator.Segment>,
        thresholdKmh: Float
    ): Float {
        var bestMeters = 0.0
        var runMeters = 0.0
        for (segment in segments) {
            if (segment.speedKmh >= thresholdKmh) {
                runMeters += segment.distanceMeters
                if (runMeters > bestMeters) bestMeters = runMeters
            } else {
                runMeters = 0.0
            }
        }
        return (bestMeters / 1000.0).toFloat()
    }
}
