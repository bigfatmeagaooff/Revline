package com.revline.tracker.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}
