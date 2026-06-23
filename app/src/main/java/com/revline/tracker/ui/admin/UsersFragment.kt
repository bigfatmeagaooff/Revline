package com.revline.tracker.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.R
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.FragmentAdminListBinding
import com.revline.tracker.ui.AdminDashboardActivity
import kotlinx.coroutines.launch

/** Tab 2 — all users; tapping one filters the Trips tab to that user. */
class UsersFragment : Fragment() {

    private var _binding: FragmentAdminListBinding? = null
    private val binding get() = _binding!!
    private lateinit var sync: SyncRepository
    private lateinit var adapter: AdminUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sync = SyncRepository.getInstance(requireContext())
        adapter = AdminUserAdapter { user ->
            (requireActivity() as AdminDashboardActivity).openUserTrips(user.id)
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load() }
        load()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = sync.getAdminUsers()
            binding.swipeRefresh.isRefreshing = false
            result.onSuccess { users ->
                adapter.submitList(users)
                binding.emptyState.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                binding.emptyState.setText(R.string.admin_users_empty)
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
