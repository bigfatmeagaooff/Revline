package com.revline.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.Trip
import com.revline.tracker.data.TripRepository
import com.revline.tracker.databinding.ActivityMainBinding
import com.revline.tracker.service.TrackingService
import com.revline.tracker.ui.TripListAdapter
import com.revline.tracker.ui.TripRow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Trip history (grouped by day) + one-tap Start Drive. Ghost/0-stat trips are filtered
 * out of the list and cleaned up on launch.
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
    ) { startDrive() }

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

        binding.startDriveButton.setOnClickListener { onStartDriveClicked() }
        binding.profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.leaderboardButton.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }

        // Clean up any leftover in-progress rows from a killed service.
        lifecycleScope.launch { repository.deleteGhostTrips() }

        observeTrips()

        val sync = SyncRepository.getInstance(this)
        if (sync.isLoggedIn) {
            lifecycleScope.launch { sync.restoreTrips() }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { SyncRepository.getInstance(this@MainActivity).sendHeartbeat() }
    }

    private fun observeTrips() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeVisibleTrips().collectLatest { trips ->
                    adapter.submitList(groupByDay(trips))
                    binding.emptyState.visibility = if (trips.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    /** Builds header + item rows, grouping trips under TODAY / YESTERDAY / "MON 23 JUN". */
    private fun groupByDay(trips: List<Trip>): List<TripRow> {
        val rows = ArrayList<TripRow>(trips.size + 4)
        var lastLabel: String? = null
        for (trip in trips) {
            val label = dayLabel(trip.startTime)
            if (label != lastLabel) {
                rows.add(TripRow.Header(label))
                lastLabel = label
            }
            rows.add(TripRow.Item(trip))
        }
        return rows
    }

    private fun dayLabel(epochMillis: Long): String {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (day) {
            today -> getString(R.string.today).uppercase(Locale.getDefault())
            today.minusDays(1) -> getString(R.string.yesterday).uppercase(Locale.getDefault())
            else -> day.format(HEADER_FMT).uppercase(Locale.getDefault())
        }
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

    companion object {
        private val HEADER_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    }
}
