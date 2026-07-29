package com.revline.tracker.util

import com.revline.tracker.data.GForcePoint
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure functions for summarizing a trip's [GForcePoint]s. No Android dependencies.
 */
object GForceCalculator {

    /**
     * Lateral G at/above this counts as actually cornering rather than straight-line
     * drift, so the average isn't dragged to ~0 by long motorway stretches.
     */
    const val CORNERING_THRESHOLD_G = 0.15f

    /** Peak G readings for a trip. All values are non-negative magnitudes. */
    data class Summary(
        val maxLateralG: Float,
        val maxAccelG: Float,
        val maxBrakingG: Float,
        /** Mean lateral G across readings above [CORNERING_THRESHOLD_G]; null if never cornering. */
        val avgCorneringG: Float?
    )

    fun summarize(points: List<GForcePoint>): Summary {
        var maxLateral = 0f
        var maxAccel = 0f
        var maxBraking = 0f // tracked as most-negative forwardG, reported as magnitude
        var corneringSum = 0.0
        var corneringCount = 0
        for (point in points) {
            val lateral = abs(point.lateralG)
            maxLateral = max(maxLateral, lateral)
            if (lateral >= CORNERING_THRESHOLD_G) {
                corneringSum += lateral
                corneringCount++
            }
            if (point.forwardG > maxAccel) maxAccel = point.forwardG
            if (point.forwardG < maxBraking) maxBraking = point.forwardG
        }
        return Summary(
            maxLateralG = maxLateral,
            maxAccelG = maxAccel,
            maxBrakingG = abs(maxBraking),
            avgCorneringG = if (corneringCount > 0) (corneringSum / corneringCount).toFloat() else null
        )
    }

    /** The single hardest braking reading (most negative forward G), or null if none. */
    fun hardestBraking(points: List<GForcePoint>): GForcePoint? =
        points.minByOrNull { it.forwardG }?.takeIf { it.forwardG < 0f }
}
