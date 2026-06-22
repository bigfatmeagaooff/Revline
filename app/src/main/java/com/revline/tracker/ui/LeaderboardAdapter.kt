package com.revline.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.R
import com.revline.tracker.data.remote.LeaderboardEntry
import com.revline.tracker.databinding.ItemLeaderboardBinding

/** Renders one leaderboard row. [valueFormatter] formats the category's stat value. */
class LeaderboardAdapter(
    private val valueFormatter: (Float) -> String
) : ListAdapter<LeaderboardEntry, LeaderboardAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLeaderboardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemLeaderboardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: LeaderboardEntry) {
            val ctx = binding.root.context
            binding.rank.text = ctx.getString(R.string.leaderboard_rank, entry.rank)
            binding.username.text = entry.username
            binding.value.text = valueFormatter(entry.value)

            val car = listOfNotNull(
                entry.carYear?.toString(),
                entry.carMake,
                entry.carModel
            ).joinToString(" ")
            binding.car.text = car.ifBlank { ctx.getString(R.string.unknown_car) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LeaderboardEntry>() {
            override fun areItemsTheSame(a: LeaderboardEntry, b: LeaderboardEntry) =
                a.rank == b.rank && a.username == b.username
            override fun areContentsTheSame(a: LeaderboardEntry, b: LeaderboardEntry) = a == b
        }
    }
}
