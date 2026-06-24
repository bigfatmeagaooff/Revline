package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivitySplashBinding

/**
 * Branded cold-start splash: the red line sweeps out, holds ~1.5s, then routes to
 * Main (if signed in) or Login. windowBackground is bg_primary so there's no white flash.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.splashLine.animate()
            .scaleX(1f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()

        Handler(Looper.getMainLooper()).postDelayed({ proceed() }, 1500)
    }

    private fun proceed() {
        val loggedIn = SyncRepository.getInstance(this).isLoggedIn
        val next = if (loggedIn) MainActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, next))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
