package com.revline.tracker.util

import android.content.Context

/**
 * The user's car identity (make/model/year), stored locally in SharedPreferences and
 * sent with trip uploads so the leaderboard can show car info from day one.
 *
 * This is intentionally NOT the future first-class Cars table — just three strings for
 * now (Phase 3). The full Car entity comes in a later phase.
 */
data class CarProfile(
    val make: String?,
    val model: String?,
    val year: Int?
) {
    companion object {
        private const val PREFS = "revline_car_profile"
        private const val KEY_MAKE = "make"
        private const val KEY_MODEL = "model"
        private const val KEY_YEAR = "year"

        fun load(context: Context): CarProfile {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val year = p.getInt(KEY_YEAR, -1).takeIf { it > 0 }
            return CarProfile(
                make = p.getString(KEY_MAKE, null)?.ifBlank { null },
                model = p.getString(KEY_MODEL, null)?.ifBlank { null },
                year = year
            )
        }

        fun save(context: Context, make: String?, model: String?, year: Int?) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MAKE, make?.trim())
                .putString(KEY_MODEL, model?.trim())
                .putInt(KEY_YEAR, year ?: -1)
                .apply()
        }
    }
}
