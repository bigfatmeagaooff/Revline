package com.revline.tracker.data

import android.content.Context
import com.revline.tracker.data.remote.ApiClient
import com.revline.tracker.data.remote.LeaderboardEntry
import com.revline.tracker.data.remote.LoginRequest
import com.revline.tracker.data.remote.RegisterRequest
import com.revline.tracker.data.remote.RevlineApi
import com.revline.tracker.data.remote.TokenStore
import com.revline.tracker.data.remote.UploadTripRequest
import com.revline.tracker.util.CarProfile
import com.revline.tracker.util.GForceCalculator
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
    data class Failed(val message: String) : UploadResult()
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
                tokenStore.save(body.accessToken, body.refreshToken, body.user.username)
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

        val trackPoints = tripRepository.getTrackPoints(tripId)
        val gForcePoints = tripRepository.getGForcePoints(tripId)

        val durationMillis = trip.actualDurationMinutes?.let { (it * 60_000f).toLong() }
            ?: ((trip.endTime ?: trip.startTime) - trip.startTime)
        val stats = TripStatsCalculator.compute(trackPoints, durationMillis, trip.distanceKm ?: 0f)
        val g = GForceCalculator.summarize(gForcePoints)
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
            maxLateralG = if (gForcePoints.isEmpty()) null else g.maxLateralG,
            maxAccelG = if (gForcePoints.isEmpty()) null else g.maxAccelG,
            maxBrakingG = if (gForcePoints.isEmpty()) null else g.maxBrakingG,
            carMake = car.make,
            carModel = car.model,
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
