package com.revline.tracker.util

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.revline.tracker.BuildConfig
import com.revline.tracker.data.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Ties Firebase Cloud Messaging to the Revline account.
 *
 * Push is optional. If this build had no `google-services.json`
 * (`BuildConfig.PUSH_CONFIGURED == false`) or Firebase fails to initialise, every
 * call here is a quiet no-op — the app still shows notifications and announcements
 * the next time it's opened (poll-on-open). See README → "Push notifications".
 */
object Push {

    private const val PREFS = "revline_push"
    private const val KEY_TOKEN = "fcm_token"

    private val scope = CoroutineScope(Dispatchers.IO)

    fun isAvailable(context: Context): Boolean =
        BuildConfig.PUSH_CONFIGURED &&
            runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    /**
     * Fetches the current FCM token and registers it with the server. Safe to call
     * on every app start and right after login; does nothing if push is unavailable
     * or nobody is signed in.
     */
    fun syncToken(context: Context) {
        val app = context.applicationContext
        if (!isAvailable(app)) return
        val sync = SyncRepository.getInstance(app)
        if (!sync.isLoggedIn) return
        scope.launch {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                ?: return@launch
            prefs(app).edit().putString(KEY_TOKEN, token).apply()
            sync.registerDevice(token)
        }
    }

    /** From [com.revline.tracker.service.RevlineMessagingService.onNewToken]. */
    fun onNewToken(context: Context, token: String) {
        val app = context.applicationContext
        prefs(app).edit().putString(KEY_TOKEN, token).apply()
        val sync = SyncRepository.getInstance(app)
        if (!sync.isLoggedIn) return
        scope.launch { sync.registerDevice(token) }
    }

    /** On logout — tell the server to stop pushing to this device. */
    suspend fun unregisterCurrent(context: Context) {
        val app = context.applicationContext
        val token = prefs(app).getString(KEY_TOKEN, null) ?: return
        SyncRepository.getInstance(app).unregisterDevice(token)
        prefs(app).edit().remove(KEY_TOKEN).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
