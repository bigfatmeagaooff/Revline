package com.revline.tracker.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    @GET("api/trips/mine")
    suspend fun getMyTrips(): Response<MineTripsResponse>

    // --- Social: following ---

    @POST("api/users/{id}/follow")
    suspend fun followUser(@Path("id") id: String): Response<FollowResponse>

    @DELETE("api/users/{id}/follow")
    suspend fun unfollowUser(@Path("id") id: String): Response<FollowResponse>

    @GET("api/users/{id}/followers")
    suspend fun followers(@Path("id") id: String, @Query("cursor") cursor: String?): Response<UserListResponse>

    @GET("api/users/{id}/following")
    suspend fun following(@Path("id") id: String, @Query("cursor") cursor: String?): Response<UserListResponse>

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") q: String): Response<UserListResponse>

    @GET("api/users/{id}/profile")
    suspend fun userProfile(@Path("id") id: String): Response<PublicProfile>

    @GET("api/users/{id}/trips")
    suspend fun userTrips(@Path("id") id: String): Response<RemoteTripsResponse>

    // --- Social: likes ---

    @POST("api/trips/{id}/like")
    suspend fun likeTrip(@Path("id") id: String): Response<LikeResponse>

    @DELETE("api/trips/{id}/like")
    suspend fun unlikeTrip(@Path("id") id: String): Response<LikeResponse>

    // --- Social: comments ---

    @GET("api/trips/{id}/comments")
    suspend fun getComments(@Path("id") id: String): Response<CommentsResponse>

    @POST("api/trips/{id}/comments")
    suspend fun postComment(@Path("id") id: String, @Body body: CommentRequest): Response<CommentResponse>

    @DELETE("api/trips/{id}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("id") id: String,
        @Path("commentId") commentId: String
    ): Response<OkResponse>

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
