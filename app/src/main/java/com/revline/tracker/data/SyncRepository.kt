package com.revline.tracker.data

import android.content.Context
import com.revline.tracker.data.remote.AdminStats
import com.revline.tracker.data.remote.AdminTrip
import com.revline.tracker.data.remote.AdminUser
import com.revline.tracker.data.remote.ApiClient
import com.revline.tracker.data.remote.FlaggedTrip
import com.revline.tracker.data.remote.LeaderboardEntry
import com.revline.tracker.data.remote.LoginRequest
import com.revline.tracker.data.remote.RegisterRequest
import com.revline.tracker.data.remote.RevlineApi
import com.revline.tracker.data.remote.TokenStore
import com.revline.tracker.data.remote.UploadTripRequest
import com.revline.tracker.data.remote.VerdictRequest
import com.revline.tracker.util.CarProfile
import com.revline.tracker.util.DeviceId
import com.revline.tracker.util.GForceCalculator
import com.revline.tracker.util.SpeedCalculator
import com.revline.tracker.util.TripStatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/** Categories exposed by the server's leaderboard endpoints. */
enum class LeaderboardCategory { TOP_SPEED, ZERO_TO_HUNDRED, LONGEST_STRETCH }

sealed class AuthOutcome {
    object Success : AuthOutcome()
    data class Error(val message: String) : AuthOutcome()
}

sealed class UploadResult {
    data class Success(val flagged: Boolean, val deduped: Boolean) : UploadResult()
    object AlreadyUploaded : UploadResult()
    object NotLoggedIn : UploadResult()
    /** Trip has no real computed stats yet (e.g. never finalized) — don't upload junk. */
    object NoValidStats : UploadResult()
    data class Failed(val message: String) : UploadResult()
}

sealed class AdminListResult {
    data class Success(val trips: List<FlaggedTrip>) : AdminListResult()
    object Forbidden : AdminListResult()
    data class Failed(val message: String) : AdminListResult()
}

sealed class VerdictResult {
    object Success : VerdictResult()
    object Forbidden : VerdictResult()
    data class Failed(val message: String) : VerdictResult()
}

/**
 * Networking seam that sits alongside the local [TripRepository]. UI/ViewModels call
 * this; it never leaks Retrofit details upward. Local persistence still goes through
 * [TripRepository], keeping the original Repository-pattern boundary intact.
 */
class SyncRepository private constructor(
    private val appContext: Context,
    private val api: RevlineApi,
    private val tokenStore: TokenStore,
    private val tripRepository: TripRepository
) {

    val isLoggedIn: Boolean get() = tokenStore.isLoggedIn
    val username: String? get() = tokenStore.username
    val userEmail: String? get() = tokenStore.email
    val isAdmin: Boolean get() = tokenStore.isAdmin

    suspend fun register(email: String, password: String, username: String): AuthOutcome =
        withContext(Dispatchers.IO) {
            runAuth { api.register(RegisterRequest(email, password, username)) }
        }

    suspend fun login(email: String, password: String): AuthOutcome =
        withContext(Dispatchers.IO) {
            runAuth { api.login(LoginRequest(email, password)) }
        }

    private suspend fun runAuth(call: suspend () -> Response<com.revline.tracker.data.remote.AuthResponse>): AuthOutcome {
        return try {
            val resp = call()
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                tokenStore.save(
                    body.accessToken,
                    body.refreshToken,
                    body.user.username,
                    body.user.email,
                    body.user.isAdmin
                )
                AuthOutcome.Success
            } else {
                AuthOutcome.Error(errorMessage(resp))
            }
        } catch (e: Exception) {
            AuthOutcome.Error(e.message ?: "Network error")
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            api.logout()
        } catch (_: Exception) {
            // best-effort; clear locally regardless
        }
        tokenStore.clear()
    }

    /**
     * Best-effort upload of a locally-saved trip. Computes the Phase 2 stats + attaches
     * the car profile, sends to the server, and stamps `uploadedAt` on success so it's
     * never re-sent. Failures are non-fatal — the trip is already safe locally.
     */
    suspend fun uploadTrip(tripId: Long): UploadResult = withContext(Dispatchers.IO) {
        if (!tokenStore.isLoggedIn) return@withContext UploadResult.NotLoggedIn

        val trip = tripRepository.getTrip(tripId) ?: return@withContext UploadResult.Failed("Trip not found")
        if (trip.uploadedAt != null) return@withContext UploadResult.AlreadyUploaded
        // Fix 1: never upload a trip without real computed stats (the source of the null
        // server rows — e.g. drives whose service was killed before finalize ran).
        if (!hasValidStats(trip)) return@withContext UploadResult.NoValidStats

        val trackPoints = tripRepository.getTrackPoints(tripId)
        val gForcePoints = tripRepository.getGForcePoints(tripId)

        val durationMillis = trip.actualDurationMinutes?.let { (it * 60_000f).toLong() }
            ?: ((trip.endTime ?: trip.startTime) - trip.startTime)
        val stats = TripStatsCalculator.compute(trackPoints, durationMillis, trip.distanceKm ?: 0f)
        // Speed-gate G readings so leaderboard/trust stats aren't polluted by stationary spikes.
        val movingGForce = SpeedCalculator.movingGForcePoints(trackPoints, gForcePoints)
        val g = GForceCalculator.summarize(movingGForce)
        val car = CarProfile.load(appContext)

        val request = UploadTripRequest(
            deviceTripId = trip.id.toString(),
            startTime = trip.startTime,
            endTime = trip.endTime ?: trip.startTime,
            predictedMinutes = trip.predictedMinutes,
            predictedDistanceKm = trip.predictedDistanceKm,
            distanceKm = trip.distanceKm,
            avgSpeedKmh = trip.avgSpeedKmh,
            topSpeedKmh = trip.topSpeedKmh,
            actualDurationMinutes = trip.actualDurationMinutes,
            idleTimeSeconds = (stats.idleMillis / 1000L).toInt(),
            movingAvgSpeedKmh = stats.movingAvgKmh,
            zeroToHundredSeconds = stats.zeroToHundredSec,
            zeroToSixtySeconds = stats.zeroToSixtySec,
            longestOver100Km = stats.longestStretchKm,
            maxLateralG = if (movingGForce.isEmpty()) null else g.maxLateralG,
            maxAccelG = if (movingGForce.isEmpty()) null else g.maxAccelG,
            maxBrakingG = if (movingGForce.isEmpty()) null else g.maxBrakingG,
            // Always send car strings (empty rather than null) so leaderboard rows are consistent.
            carMake = car.make ?: "",
            carModel = car.model ?: "",
            carYear = car.year
        )

        return@withContext try {
            val resp = api.uploadTrip(request)
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                tripRepository.updateTrip(trip.copy(uploadedAt = System.currentTimeMillis()))
                UploadResult.Success(flagged = body.flagged, deduped = body.deduped)
            } else if (resp.code() == 401) {
                UploadResult.NotLoggedIn
            } else {
                UploadResult.Failed(errorMessage(resp))
            }
        } catch (e: Exception) {
            UploadResult.Failed(e.message ?: "Network error")
        }
    }

    /** True when the trip has real stats worth sending (distance & top speed both > 0). */
    fun hasValidStats(trip: Trip): Boolean =
        (trip.distanceKm ?: 0f) > 0f && (trip.topSpeedKmh ?: 0f) > 0f

    /**
     * Fix 2: force a re-upload of a trip that may have uploaded with bad/null stats —
     * clears the local uploaded stamp, then uploads again. (The matching server-side null
     * row must be deleted first so dedup doesn't block the corrected insert.)
     */
    suspend fun reuploadTrip(tripId: Long): UploadResult = withContext(Dispatchers.IO) {
        val trip = tripRepository.getTrip(tripId)
            ?: return@withContext UploadResult.Failed("Trip not found")
        tripRepository.updateTrip(trip.copy(uploadedAt = null))
        uploadTrip(tripId)
    }

    /**
     * Fix 3: restore the user's trip history from the server (e.g. after a reinstall).
     * Inserts any server trip not already present locally, keyed on deviceTripId (the
     * original local id). Restored trips carry stats only — no GPS/G breadcrumbs.
     */
    suspend fun restoreTrips(): Int = withContext(Dispatchers.IO) {
        if (!tokenStore.isLoggedIn) return@withContext 0
        try {
            val resp = api.getMyTrips()
            val body = resp.body() ?: return@withContext 0
            var restored = 0
            for (st in body.trips) {
                val localId = st.deviceTripId?.toLongOrNull() ?: continue
                if (tripRepository.getTrip(localId) != null) continue // already present
                val startTime = parseIsoMillis(st.startTime) ?: continue
                val trip = Trip(
                    id = localId,
                    deviceId = DeviceId.get(appContext),
                    userId = null,
                    startTime = startTime,
                    endTime = parseIsoMillis(st.endTime),
                    predictedMinutes = st.predictedMinutes ?: 0,
                    predictedDistanceKm = st.predictedDistanceKm,
                    distanceKm = st.distanceKm,
                    avgSpeedKmh = st.avgSpeedKmh,
                    topSpeedKmh = st.topSpeedKmh,
                    actualDurationMinutes = st.actualDurationMinutes,
                    uploadedAt = System.currentTimeMillis(),
                    restoredFromServer = true
                )
                tripRepository.createTrip(trip)
                restored++
            }
            restored
        } catch (e: Exception) {
            0
        }
    }

    /** Aggregate stats for the profile header, computed from the user's server trips. */
    data class ProfileStats(val drives: Int, val bestTopSpeedKmh: Float, val bestDistanceKm: Float)

    suspend fun getProfileStats(): ProfileStats? = withContext(Dispatchers.IO) {
        if (!tokenStore.isLoggedIn) return@withContext null
        try {
            val trips = api.getMyTrips().body()?.trips ?: return@withContext null
            ProfileStats(
                drives = trips.size,
                bestTopSpeedKmh = trips.mapNotNull { it.topSpeedKmh }.maxOrNull() ?: 0f,
                bestDistanceKm = trips.mapNotNull { it.distanceKm }.maxOrNull() ?: 0f
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIsoMillis(iso: String?): Long? =
        iso?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }

    suspend fun leaderboard(category: LeaderboardCategory): Result<List<LeaderboardEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = when (category) {
                    LeaderboardCategory.TOP_SPEED -> api.topSpeed()
                    LeaderboardCategory.ZERO_TO_HUNDRED -> api.zeroToHundred()
                    LeaderboardCategory.LONGEST_STRETCH -> api.longestStretch()
                }
                val body = resp.body()
                if (resp.isSuccessful && body != null) {
                    Result.success(body.results)
                } else {
                    Result.failure(Exception(errorMessage(resp)))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun flaggedTrips(): AdminListResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.flaggedTrips()
            val body = resp.body()
            when {
                resp.isSuccessful && body != null -> AdminListResult.Success(body.flagged)
                resp.code() == 403 -> AdminListResult.Forbidden
                else -> AdminListResult.Failed(errorMessage(resp))
            }
        } catch (e: Exception) {
            AdminListResult.Failed(e.message ?: "Network error")
        }
    }

    suspend fun setVerdict(tripId: String, verdict: String): VerdictResult =
        withContext(Dispatchers.IO) {
            try {
                val resp = api.setVerdict(tripId, VerdictRequest(verdict))
                when {
                    resp.isSuccessful -> VerdictResult.Success
                    resp.code() == 403 -> VerdictResult.Forbidden
                    else -> VerdictResult.Failed(errorMessage(resp))
                }
            } catch (e: Exception) {
                VerdictResult.Failed(e.message ?: "Network error")
            }
        }

    /** Fire-and-forget presence ping; silently ignores failures (incl. not logged in). */
    suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        if (!tokenStore.isLoggedIn) return@withContext
        try {
            api.heartbeat()
        } catch (_: Exception) {
            // non-fatal
        }
        Unit
    }

    suspend fun getAdminStats(): Result<AdminStats> = adminCall { api.adminStats() }

    suspend fun getAdminUsers(): Result<List<AdminUser>> = adminCall { api.adminUsers() }

    suspend fun getAdminTrips(
        flaggedOnly: Boolean = false,
        userId: String? = null
    ): Result<List<AdminTrip>> = adminCall {
        api.adminTrips(if (flaggedOnly) true else null, userId)
    }

    private suspend fun <T> adminCall(call: suspend () -> Response<T>): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val resp = call()
                val body = resp.body()
                if (resp.isSuccessful && body != null) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(errorMessage(resp)))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun errorMessage(resp: Response<*>): String {
        return try {
            val raw = resp.errorBody()?.string()
            if (!raw.isNullOrBlank()) {
                org.json.JSONObject(raw).optString("error", "Request failed (${resp.code()})")
            } else {
                "Request failed (${resp.code()})"
            }
        } catch (e: Exception) {
            "Request failed (${resp.code()})"
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SyncRepository? = null

        fun getInstance(context: Context): SyncRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val ctx = context.applicationContext
                    SyncRepository(
                        ctx,
                        ApiClient.get(ctx),
                        TokenStore.getInstance(ctx),
                        TripRepository.getInstance(ctx)
                    ).also { INSTANCE = it }
                }
            }
        }
    }
}
