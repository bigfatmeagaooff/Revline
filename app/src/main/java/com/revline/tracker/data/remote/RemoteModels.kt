package com.revline.tracker.data.remote

import com.google.gson.annotations.SerializedName

// --- Auth ---

data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: RemoteUser
)

data class RefreshResponse(
    val accessToken: String
)

data class RemoteUser(
    val id: String,
    val email: String,
    val username: String,
    val isAdmin: Boolean = false
)

// --- Trip upload ---

/**
 * Flat trip summary sent to the server. Includes the Phase 2 stats (computed on the
 * client at upload time) and the locally-stored car identity, so the leaderboard has
 * everything from day one.
 */
data class UploadTripRequest(
    val deviceTripId: String,
    val startTime: Long,
    val endTime: Long,
    val predictedMinutes: Int?,
    val predictedDistanceKm: Float?,
    val distanceKm: Float?,
    val avgSpeedKmh: Float?,
    val topSpeedKmh: Float?,
    val actualDurationMinutes: Float?,
    val idleTimeSeconds: Int?,
    val movingAvgSpeedKmh: Float?,
    val zeroToHundredSeconds: Float?,
    val zeroToSixtySeconds: Float?,
    val longestOver100Km: Float?,
    val maxLateralG: Float?,
    val maxAccelG: Float?,
    val maxBrakingG: Float?,
    val carMake: String?,
    val carModel: String?,
    val carYear: Int?
)

data class UploadTripResponse(
    val tripId: String,
    val trustScore: Float,
    val flagged: Boolean,
    val deduped: Boolean = false
)

// --- Leaderboard ---

data class LeaderboardResponse(
    val category: String,
    val results: List<LeaderboardEntry>
)

data class LeaderboardEntry(
    val rank: Int,
    val username: String,
    val value: Float,
    @SerializedName("carMake") val carMake: String?,
    @SerializedName("carModel") val carModel: String?,
    @SerializedName("carYear") val carYear: Int?,
    val date: String?
)

// --- Admin ---

data class FlaggedTripsResponse(
    val flagged: List<FlaggedTrip>
)

/** One flagged trip from GET /api/admin/flagged (server returns snake_case). */
data class FlaggedTrip(
    val id: String,
    val username: String,
    @SerializedName("top_speed_kmh") val topSpeedKmh: Float?,
    @SerializedName("zero_to_hundred_seconds") val zeroToHundredSeconds: Float?,
    @SerializedName("distance_km") val distanceKm: Float?,
    @SerializedName("actual_duration_minutes") val actualDurationMinutes: Float?,
    @SerializedName("max_accel_g") val maxAccelG: Float?,
    @SerializedName("trust_score") val trustScore: Float?,
    @SerializedName("flag_reasons") val flagReasons: List<String>?
)

data class VerdictRequest(
    val verdict: String
)

data class VerdictResponse(
    val id: String?,
    @SerializedName("admin_verdict") val adminVerdict: String?,
    @SerializedName("admin_reviewed") val adminReviewed: Boolean?
)

data class OkResponse(val ok: Boolean = false)

// --- Admin dashboard (server returns camelCase for these) ---

data class AdminStats(
    val totalUsers: Int = 0,
    val totalTrips: Int = 0,
    val totalDistanceKm: Float = 0f,
    val totalDriveTimeMinutes: Float = 0f,
    val tripsToday: Int = 0,
    val flaggedPending: Int = 0,
    val activeNow: Int = 0
)

data class AdminUser(
    val id: String,
    val email: String,
    val username: String,
    val createdAt: String?,
    val lastSeen: String?,
    val tripCount: Int = 0,
    val isAdmin: Boolean = false,
    val isActive: Boolean = false
)

data class AdminTrip(
    val id: String,
    val username: String,
    val distanceKm: Float?,
    val topSpeedKmh: Float?,
    val zeroToHundredSeconds: Float?,
    val actualDurationMinutes: Float?,
    val carMake: String?,
    val carModel: String?,
    val carYear: Int?,
    val trustScore: Float?,
    val flagged: Boolean = false,
    val flagReasons: List<String>? = null,
    val adminVerdict: String?,
    val uploadedAt: String?
)
