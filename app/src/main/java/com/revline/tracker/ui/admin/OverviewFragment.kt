package com.revline.tracker.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.revline.tracker.R
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.AdminStats
import com.revline.tracker.databinding.FragmentOverviewBinding
import kotlinx.coroutines.launch

/** Tab 1 — dashboard stat overview. */
class OverviewFragment : Fragment() {

    private var _binding: FragmentOverviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var sync: SyncRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sync = SyncRepository.getInstance(requireContext())
        binding.swipeRefresh.setOnRefreshListener { load() }
        binding.cleanLeaderboardButton.setOnClickListener { cleanLeaderboard() }
        load()
    }

    /** Find leaderboard runs with no car, confirm, and take them off. */
    private fun cleanLeaderboard() {
        binding.cleanLeaderboardButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val count = sync.unknownCarCount().getOrNull()
            binding.cleanLeaderboardButton.isEnabled = true
            if (count == null) {
                toast(getString(R.string.admin_clean_failed, getString(R.string.ov_load_error)))
                return@launch
            }
            if (count == 0) {
                toast(getString(R.string.admin_clean_none))
                return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_clean_leaderboard)
                .setMessage(getString(R.string.admin_clean_confirm, count))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.admin_clean_leaderboard) { _, _ -> doPurge() }
                .show()
        }
    }

    private fun doPurge() {
        binding.cleanLeaderboardButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val r = sync.purgeUnknownCars()
            binding.cleanLeaderboardButton.isEnabled = true
            r.onSuccess { toast(getString(R.string.admin_clean_done, it)) }
                .onFailure { toast(getString(R.string.admin_clean_failed, it.message ?: "")) }
            load()
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        load() // refresh whenever this tab becomes visible
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            sync.getAdminStats()
                .onSuccess {
                    binding.swipeRefresh.isRefreshing = false
                    binding.status.visibility = View.GONE
                    bind(it)
                }
                .onFailure {
                    binding.swipeRefresh.isRefreshing = false
                    binding.status.visibility = View.VISIBLE
                    binding.status.text = getString(R.string.ov_load_error)
                }
        }
    }

    private fun bind(stats: AdminStats) {
        binding.valueUsers.text = stats.totalUsers.toString()
        binding.valueTrips.text = stats.totalTrips.toString()
        binding.valueDistance.text = getString(R.string.ov_distance_value, stats.totalDistanceKm)
        binding.valueDriveTime.text =
            getString(R.string.ov_drivetime_value, stats.totalDriveTimeMinutes / 60f)
        binding.valueTripsToday.text = stats.tripsToday.toString()
        binding.valueFlagged.text = stats.flaggedPending.toString()
        binding.valueActive.text = stats.activeNow.toString()
        binding.activeDot.visibility = if (stats.activeNow > 0) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
