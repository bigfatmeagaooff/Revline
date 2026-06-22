package com.revline.tracker.data.remote

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Attaches the access token to every request. On a 401, transparently attempts a
 * single token refresh (using the stored refresh token) and retries once before
 * giving up. The refresh call uses a bare client so it can't recurse through here.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val baseUrl: String
) : Interceptor {

    private val refreshLock = Any()
    private val bareClient = OkHttpClient()
    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val response = chain.proceed(withAuth(original, tokenStore.accessToken))

        if (response.code != 401 || tokenStore.refreshToken == null) {
            return response
        }

        // 401: try to refresh once, then retry the original request.
        val newToken = synchronized(refreshLock) { refreshAccessToken() } ?: return response
        response.close()
        return chain.proceed(withAuth(original, newToken))
    }

    private fun withAuth(request: Request, token: String?): Request {
        if (token == null) return request
        return request.newBuilder().header("Authorization", "Bearer $token").build()
    }

    private fun refreshAccessToken(): String? {
        val refresh = tokenStore.refreshToken ?: return null
        val body = gson.toJson(RefreshRequest(refresh))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl}api/auth/refresh")
            .post(body)
            .build()
        return try {
            bareClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // Refresh token is invalid/expired — force re-login.
                    tokenStore.clear()
                    return null
                }
                val parsed = gson.fromJson(resp.body?.string(), RefreshResponse::class.java)
                parsed?.accessToken?.also { tokenStore.accessToken = it }
            }
        } catch (e: Exception) {
            null
        }
    }
}
