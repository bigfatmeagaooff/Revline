package com.revline.tracker.ui

import android.content.res.ColorStateList
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
            binding.tripDate.text = DATE_FMT.format(Date(trip.startTime)).uppercase(Locale.getDefault())

            binding.tripTopSpeed.text = trip.topSpeedKmh
                ?.takeIf { it > 0f }?.roundToInt()?.toString()
                ?: ctx.getString(R.string.value_dash)
            binding.tripDistance.text = trip.distanceKm
                ?.let { String.format(Locale.getDefault(), "%.1f", it) }
                ?: ctx.getString(R.string.value_dash)

            val actual = trip.actualDurationMinutes
            if (actual != null) {
                binding.tripActual.visibility = android.view.View.VISIBLE
                binding.tripActual.text = ctx.getString(R.string.trip_actual, actual)
            } else {
                binding.tripActual.visibility = android.view.View.GONE
            }

            val uploaded = trip.uploadedAt != null
            binding.statusBadge.text = if (uploaded) "✓" else ctx.getString(R.string.value_dash)
            val tint = if (uploaded) R.color.success else R.color.text_muted
            binding.statusBadge.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, tint))

            binding.root.setOnClickListener { onClick(trip) }
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
