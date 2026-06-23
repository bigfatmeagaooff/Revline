package com.revline.tracker.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.R
import com.revline.tracker.data.remote.AdminTrip
import com.revline.tracker.databinding.ItemAdminTripBinding
import com.revline.tracker.util.RelativeTime

class AdminTripAdapter : ListAdapter<AdminTrip, AdminTripAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAdminTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemAdminTripBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(trip: AdminTrip) {
            val ctx = binding.root.context
            binding.username.text = trip.username
            binding.date.text = RelativeTime.date(trip.uploadedAt)

            val stats = buildList {
                trip.distanceKm?.let { add(ctx.getString(R.string.admin_stat_dist, it)) }
                trip.topSpeedKmh?.let { add(ctx.getString(R.string.admin_stat_top, it)) }
                trip.zeroToHundredSeconds?.let { add(ctx.getString(R.string.admin_stat_zero, it)) }
            }
            binding.stats.text = stats.joinToString("  ·  ")

            val car = listOfNotNull(trip.carYear?.toString(), trip.carMake, trip.carModel)
                .joinToString(" ")
            binding.car.text = car.ifBlank { ctx.getString(R.string.unknown_car) }

            binding.trust.text = ctx.getString(R.string.admin_trust, trip.trustScore ?: 0f)
            binding.flaggedBadge.visibility = if (trip.flagged) View.VISIBLE else View.GONE

            binding.verdict.text = when (trip.adminVerdict) {
                "approved" -> ctx.getString(R.string.admin_verdict_approved)
                "rejected" -> ctx.getString(R.string.admin_verdict_rejected)
                else -> ""
            }
            binding.verdict.visibility =
                if (trip.adminVerdict == null) View.GONE else View.VISIBLE
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AdminTrip>() {
            override fun areItemsTheSame(a: AdminTrip, b: AdminTrip) = a.id == b.id
            override fun areContentsTheSame(a: AdminTrip, b: AdminTrip) = a == b
        }
    }
}
