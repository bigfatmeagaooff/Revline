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
 */
object SpeedCalculator {

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val MPS_TO_KMH = 3.6f

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

    /** Total distance of the breadcrumb trail in kilometers. */
    fun distanceKm(points: List<TrackPoint>): Float {
        if (points.size < 2) return 0f
        var meters = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            meters += haversineMeters(a.lat, a.lon, b.lat, b.lon)
        }
        return (meters / 1000.0).toFloat()
    }

    /** Average speed in km/h given a distance and a duration. */
    fun avgSpeedKmh(distanceKm: Float, durationMillis: Long): Float {
        if (durationMillis <= 0L) return 0f
        val hours = durationMillis / 3_600_000.0
        if (hours <= 0.0) return 0f
        return (distanceKm / hours).toFloat()
    }

    /**
     * Top speed in km/h. Prefers the raw provider [TrackPoint.speedMps] values; for
     * any segment where raw speed is unavailable, falls back to speed derived from
     * the haversine distance and the time delta between consecutive points.
     */
    fun topSpeedKmh(points: List<TrackPoint>): Float {
        var topMps = 0f

        // Raw provider speeds (preferred).
        for (point in points) {
            val raw = point.speedMps
            if (raw != null && raw.isFinite() && raw >= 0f) {
                topMps = max(topMps, raw)
            }
        }

        // Derived speed for segments lacking a reliable raw reading on either end.
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val rawUnreliable = (a.speedMps == null || b.speedMps == null)
            if (!rawUnreliable) continue

            val seconds = (b.timestamp - a.timestamp) / 1000.0
            if (seconds <= 0.0) continue
            val meters = haversineMeters(a.lat, a.lon, b.lat, b.lon)
            val derivedMps = (meters / seconds).toFloat()
            if (derivedMps.isFinite() && derivedMps >= 0f) {
                topMps = max(topMps, derivedMps)
            }
        }

        return topMps * MPS_TO_KMH
    }

    fun mpsToKmh(mps: Float): Float = mps * MPS_TO_KMH
}
