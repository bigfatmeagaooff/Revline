package com.revline.tracker.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.R
import com.revline.tracker.data.AdminListResult
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.VerdictResult
import com.revline.tracker.data.remote.FlaggedTrip
import com.revline.tracker.databinding.ActivityAdminBinding
import kotlinx.coroutines.launch

/**
 * Admin-only review queue for flagged trips. Only reachable from the conditional button
 * in ProfileActivity; still re-checks server authorization (403 → finish) as a
 * belt-and-suspenders guard.
 */
class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var sync: SyncRepository
    private lateinit var adapter: FlaggedTripAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sync = SyncRepository.getInstance(this)

        adapter = FlaggedTripAdapter(
            onApprove = { verdict(it, "approved") },
            onReject = { verdict(it, "rejected") }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { load() }
        load()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        binding.emptyState.visibility = View.GONE
        lifecycleScope.launch {
            when (val result = sync.flaggedTrips()) {
                is AdminListResult.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    adapter.submitList(result.trips)
                    binding.emptyState.visibility =
                        if (result.trips.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyState.setText(R.string.admin_empty)
                }
                is AdminListResult.Forbidden -> forbidden()
                is AdminListResult.Failed -> {
                    binding.swipeRefresh.isRefreshing = false
                    adapter.submitList(emptyList())
                    binding.emptyState.visibility = View.VISIBLE
                    binding.emptyState.text = getString(R.string.admin_load_error)
                }
            }
        }
    }

    private fun verdict(trip: FlaggedTrip, verdict: String) {
        lifecycleScope.launch {
            when (sync.setVerdict(trip.id, verdict)) {
                is VerdictResult.Success -> removeFromList(trip)
                is VerdictResult.Forbidden -> forbidden()
                is VerdictResult.Failed ->
                    Toast.makeText(this@AdminActivity, R.string.admin_action_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeFromList(trip: FlaggedTrip) {
        val remaining = adapter.currentList.filterNot { it.id == trip.id }
        adapter.submitList(remaining)
        binding.emptyState.visibility = if (remaining.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyState.setText(R.string.admin_empty)
    }

    private fun forbidden() {
        binding.swipeRefresh.isRefreshing = false
        Toast.makeText(this, R.string.admin_forbidden, Toast.LENGTH_LONG).show()
        finish()
    }
}
