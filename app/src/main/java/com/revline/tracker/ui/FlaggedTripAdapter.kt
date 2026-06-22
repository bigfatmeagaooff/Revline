package com.revline.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.R
import com.revline.tracker.data.remote.FlaggedTrip
import com.revline.tracker.databinding.ItemFlaggedTripBinding

/** Renders one flagged trip with its stats, flag reasons, trust score, and verdict buttons. */
class FlaggedTripAdapter(
    private val onApprove: (FlaggedTrip) -> Unit,
    private val onReject: (FlaggedTrip) -> Unit
) : ListAdapter<FlaggedTrip, FlaggedTripAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFlaggedTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemFlaggedTripBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(trip: FlaggedTrip) {
            val ctx = binding.root.context
            binding.username.text = trip.username

            val parts = buildList {
                trip.topSpeedKmh?.let { add(ctx.getString(R.string.admin_stat_top, it)) }
                trip.zeroToHundredSeconds?.let { add(ctx.getString(R.string.admin_stat_zero, it)) }
                trip.distanceKm?.let { add(ctx.getString(R.string.admin_stat_dist, it)) }
                trip.actualDurationMinutes?.let { add(ctx.getString(R.string.admin_stat_dur, it)) }
            }
            binding.stats.text = parts.joinToString("  ·  ")

            binding.reasons.text = trip.flagReasons
                ?.joinToString("\n") { "• $it" }
                .orEmpty()

            binding.trust.text = ctx.getString(R.string.admin_trust, trip.trustScore ?: 0f)

            binding.approveButton.setOnClickListener { onApprove(trip) }
            binding.rejectButton.setOnClickListener { onReject(trip) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FlaggedTrip>() {
            override fun areItemsTheSame(a: FlaggedTrip, b: FlaggedTrip) = a.id == b.id
            override fun areContentsTheSame(a: FlaggedTrip, b: FlaggedTrip) = a == b
        }
    }
}
