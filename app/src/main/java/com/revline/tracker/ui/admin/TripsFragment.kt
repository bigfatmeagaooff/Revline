package com.revline.tracker.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.R
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.FragmentAdminListBinding
import com.revline.tracker.ui.AdminDashboardActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Tab 3 — all trips, optionally filtered to one user (set from the Users tab). */
class TripsFragment : Fragment() {

    private var _binding: FragmentAdminListBinding? = null
    private val binding get() = _binding!!
    private lateinit var sync: SyncRepository
    private lateinit var adapter: AdminTripAdapter

    private var currentFilter: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sync = SyncRepository.getInstance(requireContext())
        adapter = AdminTripAdapter()
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load(currentFilter) }
        binding.filterBanner.setOnClickListener {
            (requireActivity() as AdminDashboardActivity).clearUserFilter()
        }

        // React to the shared user filter (tapping a user in the Users tab).
        val host = requireActivity() as AdminDashboardActivity
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.userFilter.collectLatest { userId ->
                    currentFilter = userId
                    binding.filterBanner.visibility = if (userId == null) View.GONE else View.VISIBLE
                    load(userId)
                }
            }
        }
    }

    private fun load(userId: String?) {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = sync.getAdminTrips(userId = userId)
            binding.swipeRefresh.isRefreshing = false
            result.onSuccess { trips ->
                adapter.submitList(trips)
                binding.emptyState.visibility = if (trips.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyState.setText(R.string.admin_trips_empty)
            }.onFailure {
                adapter.submitList(emptyList())
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.setText(R.string.admin_load_error)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
