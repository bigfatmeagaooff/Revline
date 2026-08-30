package com.revline.tracker.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

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

    /** The signed-in user's profile picture URL (relative or absolute), or null. */
    var avatarUrl: String?
        get() = prefs.getString(KEY_AVATAR, null)?.ifBlank { null }
        set(value) = prefs.edit().putString(KEY_AVATAR, value).apply()

    val email: String? get() = prefs.getString(KEY_EMAIL, null)

    /**
     * The logged-in user's server id. Stored at login since Phase 4; sessions created
     * before that never saved it, so fall back to decoding the JWT's `sub` claim (and
     * backfill the pref so the decode only ever happens once).
     */
    val userId: String?
        get() {
            prefs.getString(KEY_USER_ID, null)?.let { return it }
            val sub = accessToken?.let { jwtSub(it) } ?: return null
            prefs.edit().putString(KEY_USER_ID, sub).apply()
            return sub
        }

    /** Extracts the `sub` claim from a JWT without verifying it (display-only use). */
    private fun jwtSub(jwt: String): String? = runCatching {
        val payload = jwt.split(".").getOrNull(1) ?: return null
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        JSONObject(json).optString("sub").ifBlank { null }
    }.getOrNull()

    /** Persisted at login so the admin entry point survives restarts without an API call. */
    val isAdmin: Boolean get() = prefs.getBoolean(KEY_IS_ADMIN, false)

    /** The account's car — the source of truth for leaderboard uploads. */
    val carMake: String? get() = prefs.getString(KEY_CAR_MAKE, null)?.ifBlank { null }
    val carModel: String? get() = prefs.getString(KEY_CAR_MODEL, null)?.ifBlank { null }
    val carYear: Int? get() = prefs.getInt(KEY_CAR_YEAR, -1).takeIf { it > 0 }
    val hasCar: Boolean get() = !carMake.isNullOrBlank() && !carModel.isNullOrBlank()

    fun saveCar(make: String?, model: String?, year: Int?) {
        prefs.edit()
            .putString(KEY_CAR_MAKE, make?.trim())
            .putString(KEY_CAR_MODEL, model?.trim())
            .putInt(KEY_CAR_YEAR, year ?: -1)
            .apply()
    }

    val isLoggedIn: Boolean get() = accessToken != null && refreshToken != null

    fun save(
        accessToken: String,
        refreshToken: String,
        userId: String,
        username: String,
        email: String,
        isAdmin: Boolean,
        carMake: String? = null,
        carModel: String? = null,
        carYear: Int? = null,
        avatarUrl: String? = null
    ) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_IS_ADMIN, isAdmin)
            .putString(KEY_CAR_MAKE, carMake?.trim())
            .putString(KEY_CAR_MODEL, carModel?.trim())
            .putInt(KEY_CAR_YEAR, carYear ?: -1)
            .putString(KEY_AVATAR, avatarUrl)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE = "revline_secure_prefs"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_CAR_MAKE = "car_make"
        private const val KEY_CAR_MODEL = "car_model"
        private const val KEY_CAR_YEAR = "car_year"
        private const val KEY_AVATAR = "avatar_url"

        @Volatile
        private var INSTANCE: TokenStore? = null

        fun getInstance(context: Context): TokenStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun create(context: Context): TokenStore {
            return try {
                TokenStore(buildEncryptedPrefs(context))
            } catch (e: Exception) {
                // The encrypted prefs file can become undecryptable (device restore,
                // keystore reset). Rather than crash at launch forever, wipe the stale
                // file and start fresh — the user just signs in again.
                context.deleteSharedPreferences(PREFS_FILE)
                TokenStore(buildEncryptedPrefs(context))
            }
        }

        private fun buildEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
