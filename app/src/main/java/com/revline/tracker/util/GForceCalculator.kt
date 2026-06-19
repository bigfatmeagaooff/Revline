package com.revline.tracker.util

import com.revline.tracker.data.GForcePoint
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure functions for summarizing a trip's [GForcePoint]s. No Android dependencies.
 */
object GForceCalculator {

    /** Peak G readings for a trip. All values are non-negative magnitudes. */
    data class Summary(
        val maxLateralG: Float,
        val maxAccelG: Float,
        val maxBrakingG: Float
    )

    fun summarize(points: List<GForcePoint>): Summary {
        var maxLateral = 0f
        var maxAccel = 0f
        var maxBraking = 0f // tracked as most-negative forwardG, reported as magnitude
        for (point in points) {
            maxLateral = max(maxLateral, abs(point.lateralG))
            if (point.forwardG > maxAccel) maxAccel = point.forwardG
            if (point.forwardG < maxBraking) maxBraking = point.forwardG
        }
        return Summary(
            maxLateralG = maxLateral,
            maxAccelG = maxAccel,
            maxBrakingG = abs(maxBraking)
        )
    }
}
