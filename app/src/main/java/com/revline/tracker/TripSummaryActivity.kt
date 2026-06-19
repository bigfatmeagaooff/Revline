package com.revline.tracker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.revline.tracker.data.Trip
import com.revline.tracker.data.TripRepository
import com.revline.tracker.databinding.ActivityTripSummaryBinding
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Post-trip stats: distance, duration, avg/top speed, and the headline
 * predicted-vs-actual delta. Read-only — used both right after a drive and when
 * opened from history.
 */
class TripSummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripSummaryBinding
    private lateinit var repository: TripRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TripRepository.getInstance(this)

        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
        if (tripId <= 0L) {
            finish()
            return
        }

        lifecycleScope.launch {
            val trip = repository.getTrip(tripId)
            if (trip == null) {
                finish()
            } else {
                bind(trip)
            }
        }
    }

    private fun bind(trip: Trip) {
        binding.distanceValue.text = trip.distanceKm?.let {
            getString(R.string.summary_distance_value, it)
        } ?: getString(R.string.value_dash)

        binding.durationValue.text = trip.actualDurationMinutes?.let {
            getString(R.string.summary_duration_value, it)
        } ?: getString(R.string.value_dash)

        binding.avgSpeedValue.text = trip.avgSpeedKmh?.let {
            getString(R.string.summary_speed_value, it)
        } ?: getString(R.string.value_dash)

        binding.topSpeedValue.text = trip.topSpeedKmh?.let {
            getString(R.string.summary_speed_value, it)
        } ?: getString(R.string.value_dash)

        bindPredictionDelta(trip)
    }

    private fun bindPredictionDelta(trip: Trip) {
        val actualMinutes = trip.actualDurationMinutes
        if (actualMinutes == null) {
            binding.predictionDelta.visibility = View.GONE
            return
        }

        val predicted = trip.predictedMinutes
        val actualRounded = actualMinutes.roundToInt()
        val delta = actualRounded - predicted
        val deltaText = when {
            delta > 0 -> getString(R.string.delta_over, delta)
            delta < 0 -> getString(R.string.delta_under, abs(delta))
            else -> getString(R.string.delta_exact)
        }

        binding.predictionDelta.visibility = View.VISIBLE
        binding.predictionDelta.text = getString(
            R.string.summary_prediction,
            predicted,
            actualRounded,
            deltaText
        )
    }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }
}
