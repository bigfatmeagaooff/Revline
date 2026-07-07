package com.revline.tracker.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * App-wide edge-to-edge inset handling. targetSdk 35 forces edge-to-edge on Android 15+,
 * so every screen must pad itself clear of the status bar, nav bar, and keyboard — not
 * just Drives. One call on the root view in each Activity's onCreate covers all four
 * edges (including the IME, so bottom-anchored inputs rise above the keyboard).
 */
object EdgeToEdge {

    fun apply(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }
}
