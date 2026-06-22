package com.revline.tracker.data.remote

import android.content.Context
import com.revline.tracker.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Builds the shared [RevlineApi] (Retrofit) wired with auth + logging. */
object ApiClient {

    @Volatile
    private var api: RevlineApi? = null

    fun get(context: Context): RevlineApi {
        return api ?: synchronized(this) {
            api ?: build(context.applicationContext).also { api = it }
        }
    }

    private fun build(context: Context): RevlineApi {
        val baseUrl = BuildConfig.API_BASE_URL
        val tokenStore = TokenStore.getInstance(context)

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore, baseUrl))
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RevlineApi::class.java)
    }
}
