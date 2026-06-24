package com.revline.tracker

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.revline.tracker.data.LeaderboardCategory
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivityLeaderboardBinding
import com.revline.tracker.ui.LeaderboardAdapter
import kotlinx.coroutines.launch
import java.util.Locale

/** Public leaderboard with category tabs and pull-to-refresh. */
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

        binding.backButton.setOnClickListener { finish() }

        adapter = LeaderboardAdapter(unitFor(category), formatFor(category))
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent_red))
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.bg_elevated)
        )
        binding.swipeRefresh.setOnRefreshListener { load() }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                category = when (tab.position) {
                    1 -> LeaderboardCategory.ZERO_TO_HUNDRED
                    2 -> LeaderboardCategory.LONGEST_STRETCH
                    else -> LeaderboardCategory.TOP_SPEED
                }
                adapter.unit = unitFor(category)
                adapter.numberFormat = formatFor(category)
                load()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

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
                binding.emptyState.setText(R.string.leaderboard_empty_v2)
            }.onFailure {
                adapter.submitList(emptyList())
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.text = getString(R.string.leaderboard_error)
            }
        }
    }

    private fun unitFor(category: LeaderboardCategory): String = when (category) {
        LeaderboardCategory.TOP_SPEED -> getString(R.string.unit_kmh)
        LeaderboardCategory.ZERO_TO_HUNDRED -> getString(R.string.unit_s)
        LeaderboardCategory.LONGEST_STRETCH -> getString(R.string.unit_km)
    }

    private fun formatFor(category: LeaderboardCategory): (Float) -> String = when (category) {
        LeaderboardCategory.TOP_SPEED -> { v -> String.format(Locale.getDefault(), "%.0f", v) }
        else -> { v -> String.format(Locale.getDefault(), "%.1f", v) }
    }
}
