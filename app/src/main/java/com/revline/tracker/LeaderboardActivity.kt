package com.revline.tracker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.data.LeaderboardCategory
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivityLeaderboardBinding
import com.revline.tracker.ui.LeaderboardAdapter
import kotlinx.coroutines.launch

/** Public leaderboard with a category toggle and pull-to-refresh. */
class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private lateinit var sync: SyncRepository
    private lateinit var adapter: LeaderboardAdapter

    private var category = LeaderboardCategory.TOP_SPEED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sync = SyncRepository.getInstance(this)

        adapter = LeaderboardAdapter { formatValue(it) }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.categoryToggle.check(R.id.catTopSpeed)
        binding.categoryToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            category = when (checkedId) {
                R.id.catZeroHundred -> LeaderboardCategory.ZERO_TO_HUNDRED
                R.id.catLongest -> LeaderboardCategory.LONGEST_STRETCH
                else -> LeaderboardCategory.TOP_SPEED
            }
            load()
        }

        binding.swipeRefresh.setOnRefreshListener { load() }
        load()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        binding.emptyState.visibility = View.GONE
        lifecycleScope.launch {
            val result = sync.leaderboard(category)
            binding.swipeRefresh.isRefreshing = false
            result.onSuccess { entries ->
                adapter.submitList(entries)
                binding.emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyState.setText(R.string.leaderboard_empty)
            }.onFailure {
                adapter.submitList(emptyList())
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.text = getString(R.string.leaderboard_error)
            }
        }
    }

    private fun formatValue(value: Float): String = when (category) {
        LeaderboardCategory.TOP_SPEED -> getString(R.string.summary_speed_value, value)
        LeaderboardCategory.ZERO_TO_HUNDRED -> getString(R.string.detail_seconds_value, value)
        LeaderboardCategory.LONGEST_STRETCH -> getString(R.string.detail_distance_value, value)
    }
}
