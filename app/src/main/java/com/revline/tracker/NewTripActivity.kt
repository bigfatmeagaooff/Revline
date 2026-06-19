package com.revline.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.revline.tracker.databinding.ActivityNewTripBinding
import com.revline.tracker.service.TrackingService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Collects the Google-Maps-predicted time/distance, drives the staged location
 * permission flow, and then shows a dead-simple in-progress screen (elapsed timer
 * + big Stop button) backed by [TrackingService].
 */
class NewTripActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewTripBinding

    // Pending trip inputs captured when Start is tapped, started once permissions resolve.
    private var pendingMinutes: Int = 0
    private var pendingDistanceKm: Float? = null

    private var elapsedStartTime: Long = 0L
    private val elapsedTicker = object : Runnable {
        override fun run() {
            val seconds = (System.currentTimeMillis() - elapsedStartTime) / 1000L
            binding.elapsedTime.text = formatElapsed(seconds)
            binding.elapsedTime.postDelayed(this, 1000L)
        }
    }

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fineGranted) {
            Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        maybeRequestBackgroundThenStart()
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Background location is best-effort; tracking still starts without it.
        startTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewTripBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startDriveButton.setOnClickListener { onStartClicked() }
        binding.stopDriveButton.setOnClickListener { TrackingService.stop(this) }

        observeTrackingState()
        observeGForce()
    }

    private fun onStartClicked() {
        val minutesText = binding.predictedMinutesInput.text?.toString()?.trim().orEmpty()
        val minutes = minutesText.toIntOrNull()
        if (minutes == null || minutes <= 0) {
            binding.predictedMinutesLayout.error = getString(R.string.error_minutes_required)
            return
        }
        binding.predictedMinutesLayout.error = null

        val distanceText = binding.predictedDistanceInput.text?.toString()?.trim().orEmpty()
        pendingMinutes = minutes
        pendingDistanceKm = distanceText.toFloatOrNull()

        requestPermissionsThenStart()
    }

    private fun requestPermissionsThenStart() {
        val needed = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isNotEmpty()) {
            foregroundPermissionLauncher.launch(needed.toTypedArray())
        } else {
            maybeRequestBackgroundThenStart()
        }
    }

    /**
     * Android requires background location to be requested as a separate, second prompt
     * after fine location is already granted — it won't grant both in one dialog.
     */
    private fun maybeRequestBackgroundThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            startTracking()
        }
    }

    private fun startTracking() {
        TrackingService.start(this, pendingMinutes, pendingDistanceKm)
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
                        showInProgress(state.startTime)
                    } else {
                        showForm()
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

    private fun showForm() {
        binding.formPanel.visibility = android.view.View.VISIBLE
        binding.inProgressPanel.visibility = android.view.View.GONE
        binding.elapsedTime.removeCallbacks(elapsedTicker)
    }

    private fun showInProgress(startTime: Long) {
        binding.formPanel.visibility = android.view.View.GONE
        binding.inProgressPanel.visibility = android.view.View.VISIBLE
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

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun formatElapsed(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return getString(R.string.elapsed_format, minutes, seconds)
    }
}
