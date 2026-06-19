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

    data class Stats(
        val idleMillis: Long,
        val movingAvgKmh: Float?,
        val zeroToHundredSec: Float?,
        val zeroToSixtySec: Float?,
        val longestStretchKm: Float,
        val longestStretchThresholdKmh: Int
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

        return Stats(
            idleMillis = idleMillis,
            movingAvgKmh = movingAvgKmh,
            zeroToHundredSec = fastestLaunchSeconds(segments, 100f),
            zeroToSixtySec = fastestLaunchSeconds(segments, 60f),
            longestStretchKm = longestStretchKm(segments, FAST_STRETCH_THRESHOLD_KMH.toFloat()),
            longestStretchThresholdKmh = FAST_STRETCH_THRESHOLD_KMH
        )
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
