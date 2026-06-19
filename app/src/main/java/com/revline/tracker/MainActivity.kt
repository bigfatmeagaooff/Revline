package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.data.TripRepository
import com.revline.tracker.databinding.ActivityMainBinding
import com.revline.tracker.ui.TripListAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Trip history, most recent first. Entry point of the app. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TripRepository
    private lateinit var adapter: TripListAdapter

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

        binding.newTripFab.setOnClickListener {
            startActivity(Intent(this, NewTripActivity::class.java))
        }

        observeTrips()
    }

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
