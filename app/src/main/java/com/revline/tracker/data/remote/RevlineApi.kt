package com.revline.tracker.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit interface for the Revline server. */
interface RevlineApi {

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/trips/upload")
    suspend fun uploadTrip(@Body body: UploadTripRequest): Response<UploadTripResponse>

    @GET("api/leaderboard/top-speed")
    suspend fun topSpeed(): Response<LeaderboardResponse>

    @GET("api/leaderboard/zero-to-hundred")
    suspend fun zeroToHundred(): Response<LeaderboardResponse>

    @GET("api/leaderboard/longest-stretch")
    suspend fun longestStretch(): Response<LeaderboardResponse>

    @POST("api/users/heartbeat")
    suspend fun heartbeat(): Response<OkResponse>

    @GET("api/admin/flagged")
    suspend fun flaggedTrips(): Response<FlaggedTripsResponse>

    @GET("api/admin/stats")
    suspend fun adminStats(): Response<AdminStats>

    @GET("api/admin/users")
    suspend fun adminUsers(): Response<List<AdminUser>>

    @GET("api/admin/trips")
    suspend fun adminTrips(
        @Query("flagged") flagged: Boolean?,
        @Query("userId") userId: String?
    ): Response<List<AdminTrip>>

    @POST("api/admin/trips/{id}/verdict")
    suspend fun setVerdict(
        @Path("id") tripId: String,
        @Body body: VerdictRequest
    ): Response<VerdictResponse>
}
