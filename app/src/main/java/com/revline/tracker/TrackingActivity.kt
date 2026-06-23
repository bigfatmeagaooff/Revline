package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.revline.tracker.databinding.ActivityTrackingBinding
import com.revline.tracker.service.TrackingService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dead-simple in-progress screen: elapsed timer, live G readout, and a big Stop button.
 * Started immediately after the user taps Start Drive (no pre-drive entry as of Phase 3.3).
 */
class TrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackingBinding

    private var elapsedStartTime: Long = 0L
    private val elapsedTicker = object : Runnable {
        override fun run() {
            val seconds = (System.currentTimeMillis() - elapsedStartTime) / 1000L
            binding.elapsedTime.text = formatElapsed(seconds)
            binding.elapsedTime.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.stopDriveButton.setOnClickListener { TrackingService.stop(this) }

        observeTrackingState()
        observeGForce()
    }

    private fun observeTrackingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TrackingService.state.collectLatest { state ->
                    val finishedId = state.finishedTripId
                    if (finishedId != null) {
                        TrackingService.consumeFinishedTrip()
                        goToSummary(finishedId)
                        return@collectLatest
                    }
                    if (state.isTracking && state.startTime != null) {
                        startTicker(state.startTime)
                    } else if (!state.isTracking) {
                        // Service stopped without a finished trip (e.g. permission lost) — bail out.
                        finish()
                    }
                }
            }
        }
    }

    private fun observeGForce() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TrackingService.gForce.collectLatest { reading ->
                    binding.gforceLive.text = getString(
                        R.string.gforce_live,
                        reading.lateralG,
                        reading.forwardG
                    )
                }
            }
        }
    }

    private fun startTicker(startTime: Long) {
        elapsedStartTime = startTime
        binding.elapsedTime.removeCallbacks(elapsedTicker)
        binding.elapsedTime.post(elapsedTicker)
    }

    private fun goToSummary(tripId: Long) {
        binding.elapsedTime.removeCallbacks(elapsedTicker)
        startActivity(
            Intent(this, TripSummaryActivity::class.java)
                .putExtra(TripSummaryActivity.EXTRA_TRIP_ID, tripId)
        )
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.elapsedTime.removeCallbacks(elapsedTicker)
    }

    private fun formatElapsed(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return getString(R.string.elapsed_format, minutes, seconds)
    }
}
