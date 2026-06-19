package com.revline.tracker.util

import android.content.Context
import java.util.UUID

/**
 * Generates and persists a locally-generated pseudo-user-id for this install.
 *
 * Every entity carries this [deviceId] now (before any auth exists) so that when
 * accounts are introduced later we backfill ownership instead of migrating data.
 */
object DeviceId {

    private const val PREFS_NAME = "revline_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    fun get(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }
}
