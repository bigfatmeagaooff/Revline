package com.revline.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.R
import com.revline.tracker.data.Trip
import com.revline.tracker.databinding.ItemTripBinding
import com.revline.tracker.databinding.ItemTripHeaderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** A row in the grouped trip list: either a date header or a trip card. */
sealed class TripRow {
    data class Header(val label: String) : TripRow()
    data class Item(val trip: Trip) : TripRow()
}

class TripListAdapter(
    private val onClick: (Trip) -> Unit
) : ListAdapter<TripRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is TripRow.Header -> TYPE_HEADER
        is TripRow.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemTripHeaderBinding.inflate(inflater, parent, false))
        } else {
            TripVH(ItemTripBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is TripRow.Header -> (holder as HeaderVH).bind(row)
            is TripRow.Item -> (holder as TripVH).bind(row.trip)
        }
    }

    class HeaderVH(private val binding: ItemTripHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: TripRow.Header) {
            binding.headerLabel.text = header.label
        }
    }

    inner class TripVH(private val binding: ItemTripBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(trip: Trip) {
            val ctx = binding.root.context
            val dash = ctx.getString(R.string.value_dash)

            binding.tripDate.text = ctx.getString(
                R.string.trip_stub,
                DATE_FMT.format(Date(trip.startTime)).uppercase(Locale.getDefault())
            )

            binding.tripTopSpeed.text = trip.topSpeedKmh
                ?.takeIf { it > 0f }?.roundToInt()?.toString() ?: dash

            // label · value readout: distance · elapsed · avg
            val parts = mutableListOf<String>()
            trip.distanceKm?.let { parts += String.format(Locale.getDefault(), "%.1f km", it) }
            trip.actualDurationMinutes?.let { parts += formatDuration(it) }
            trip.avgSpeedKmh?.takeIf { it > 0f }?.let {
                parts += ctx.getString(R.string.trip_avg, it.roundToInt())
            }
            binding.tripReadout.text = parts.joinToString("   ·   ")

            // Spine: redline once the run is on the leaderboard, faint while local-only.
            val filed = trip.uploadedAt != null
            binding.statusSpine.setBackgroundColor(
                ContextCompat.getColor(ctx, if (filed) R.color.redline else R.color.print_faint)
            )

            binding.root.setOnClickListener { onClick(trip) }
        }

        private fun formatDuration(minutes: Float): String {
            val total = (minutes * 60).roundToInt()
            return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        private val DATE_FMT = SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault())

        private val DIFF = object : DiffUtil.ItemCallback<TripRow>() {
            override fun areItemsTheSame(a: TripRow, b: TripRow): Boolean = when {
                a is TripRow.Header && b is TripRow.Header -> a.label == b.label
                a is TripRow.Item && b is TripRow.Item -> a.trip.id == b.trip.id
                else -> false
            }

            override fun areContentsTheSame(a: TripRow, b: TripRow) = a == b
        }
    }
}
