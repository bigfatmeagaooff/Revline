package com.revline.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.R
import com.revline.tracker.data.Trip
import com.revline.tracker.databinding.ItemTripBinding
import java.text.DateFormat
import java.util.Date

class TripListAdapter(
    private val onClick: (Trip) -> Unit
) : ListAdapter<Trip, TripListAdapter.TripViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TripViewHolder(
        private val binding: ItemTripBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(trip: Trip) {
            val context = binding.root.context
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            binding.tripDate.text = dateFormat.format(Date(trip.startTime))

            val actual = trip.actualDurationMinutes
            val hasPrediction = trip.predictedMinutes > 0 // 0 = not set (Phase 3.3)
            binding.tripTimes.text = when {
                actual != null && hasPrediction ->
                    context.getString(R.string.trip_item_times, trip.predictedMinutes, actual)
                actual != null ->
                    context.getString(R.string.trip_item_actual_only, actual)
                hasPrediction ->
                    context.getString(R.string.trip_item_times_in_progress, trip.predictedMinutes)
                else ->
                    context.getString(R.string.trip_item_in_progress_only)
            }

            val distance = trip.distanceKm
            binding.tripDistance.text = if (distance != null) {
                context.getString(R.string.trip_item_distance, distance)
            } else {
                context.getString(R.string.value_dash)
            }

            val topSpeed = trip.topSpeedKmh
            binding.tripTopSpeed.text = if (topSpeed != null) {
                context.getString(R.string.trip_item_top_speed, topSpeed)
            } else {
                context.getString(R.string.value_dash)
            }

            binding.root.setOnClickListener { onClick(trip) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Trip>() {
            override fun areItemsTheSame(oldItem: Trip, newItem: Trip) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Trip, newItem: Trip) =
                oldItem == newItem
        }
    }
}
