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
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.TripRepository
import com.revline.tracker.databinding.ActivityMainBinding
import com.revline.tracker.service.TrackingService
import com.revline.tracker.ui.TripListAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Trip history + one-tap Start Drive (Phase 3.3: no pre-drive entry screen). The staged
 * location-permission flow lives here now since this is where a drive begins.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TripRepository
    private lateinit var adapter: TripListAdapter

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
        startDrive()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TripRepository.getInstance(this)

        adapter = TripListAdapter { trip ->
            startActivity(
                Intent(this, TripSummaryActivity::class.java)
                    .putExtra(TripSummaryActivity.EXTRA_TRIP_ID, trip.id)
            )
        }
        binding.tripList.layoutManager = LinearLayoutManager(this)
        binding.tripList.adapter = adapter

        binding.newTripFab.setOnClickListener { onStartDriveClicked() }

        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_leaderboard -> {
                    startActivity(Intent(this, LeaderboardActivity::class.java))
                    true
                }
                R.id.action_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }

        observeTrips()

        // Fix 3: pull any server-side trip history missing locally (e.g. after a reinstall).
        val sync = SyncRepository.getInstance(this)
        if (sync.isLoggedIn) {
            lifecycleScope.launch { sync.restoreTrips() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Mark the user active on foreground (covers periods outside an active drive).
        lifecycleScope.launch { SyncRepository.getInstance(this@MainActivity).sendHeartbeat() }
    }

    // --- Start Drive + staged permissions ---

    private fun onStartDriveClicked() {
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
            startDrive()
        }
    }

    private fun startDrive() {
        TrackingService.start(this)
        startActivity(Intent(this, TrackingActivity::class.java))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun observeTrips() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeTrips().collectLatest { trips ->
                    adapter.submitList(trips)
                    binding.emptyState.visibility =
                        if (trips.isEmpty()) android.view.View.VISIBLE
                        else android.view.View.GONE
                }
            }
        }
    }
}
