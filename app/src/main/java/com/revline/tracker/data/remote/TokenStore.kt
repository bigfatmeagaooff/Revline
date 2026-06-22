package com.revline.tracker.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Securely persists the JWT access/refresh tokens and the logged-in user, using
 * [EncryptedSharedPreferences] (Jetpack Security) — JWTs are never stored in plain
 * SharedPreferences.
 */
class TokenStore private constructor(private val prefs: SharedPreferences) {

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    /** Persisted at login so the admin entry point survives restarts without an API call. */
    val isAdmin: Boolean get() = prefs.getBoolean(KEY_IS_ADMIN, false)

    val isLoggedIn: Boolean get() = accessToken != null && refreshToken != null

    fun save(accessToken: String, refreshToken: String, username: String, isAdmin: Boolean) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_USERNAME, username)
            .putBoolean(KEY_IS_ADMIN, isAdmin)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_ADMIN = "is_admin"

        @Volatile
        private var INSTANCE: TokenStore? = null

        fun getInstance(context: Context): TokenStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun create(context: Context): TokenStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "revline_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return TokenStore(prefs)
        }
    }
}
