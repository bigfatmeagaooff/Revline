package com.revline.tracker.util

import android.content.Context

/** Simple user preferences. Manual start/stop is the default; nothing here is on by default. */
object AppSettings {

    private const val PREFS = "revline_settings"
    private const val KEY_AUTO_DETECT = "auto_detect_enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Automatic trip detection. Off unless the user explicitly opts in. */
    fun isAutoDetectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_DETECT, false)

    fun setAutoDetectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DETECT, enabled).apply()
    }
}
