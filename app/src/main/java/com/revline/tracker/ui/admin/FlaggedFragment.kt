package com.revline.tracker.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.R
import com.revline.tracker.data.AdminListResult
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.VerdictResult
import com.revline.tracker.data.remote.FlaggedTrip
import com.revline.tracker.databinding.FragmentAdminListBinding
import com.revline.tracker.ui.FlaggedTripAdapter
import kotlinx.coroutines.launch

/** Tab 4 — flagged review queue (Phase 3.1 behaviour, moved into the dashboard). */
class FlaggedFragment : Fragment() {

    private var _binding: FragmentAdminListBinding? = null
    private val binding get() = _binding!!
    private lateinit var sync: SyncRepository
    private lateinit var adapter: FlaggedTripAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sync = SyncRepository.getInstance(requireContext())
        adapter = FlaggedTripAdapter(
            onApprove = { verdict(it, "approved") },
            onReject = { verdict(it, "rejected") }
        )
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load() }
        load()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
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
                    binding.emptyState.setText(R.string.admin_load_error)
                }
            }
        }
    }

    private fun verdict(trip: FlaggedTrip, verdict: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (sync.setVerdict(trip.id, verdict)) {
                is VerdictResult.Success -> removeFromList(trip)
                is VerdictResult.Forbidden -> forbidden()
                is VerdictResult.Failed ->
                    Toast.makeText(requireContext(), R.string.admin_action_failed, Toast.LENGTH_SHORT).show()
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
        Toast.makeText(requireContext(), R.string.admin_forbidden, Toast.LENGTH_LONG).show()
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
