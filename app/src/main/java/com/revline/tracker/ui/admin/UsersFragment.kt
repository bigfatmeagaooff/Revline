package com.revline.tracker.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.revline.tracker.R
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.AdminResetCodeResponse
import com.revline.tracker.data.remote.AdminUser
import com.revline.tracker.databinding.FragmentAdminListBinding
import com.revline.tracker.ui.AdminDashboardActivity
import kotlinx.coroutines.launch

/** Tab 2 — all users; tap filters the Trips tab to that user, long-press issues a reset code. */
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
        adapter = AdminUserAdapter(
            onClick = { user ->
                (requireActivity() as AdminDashboardActivity).openUserTrips(user.id)
            },
            onLongClick = { user -> confirmResetCode(user) }
        )
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load() }
        load()
    }

    private fun confirmResetCode(user: AdminUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_reset_password)
            .setMessage(getString(R.string.admin_reset_code_confirm, user.username))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.admin_reset_password) { _, _ -> issueResetCode(user) }
            .show()
    }

    private fun issueResetCode(user: AdminUser) {
        viewLifecycleOwner.lifecycleScope.launch {
            sync.issueResetCode(user.id)
                .onSuccess { showResetCode(it) }
                .onFailure {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(
                            getString(
                                R.string.admin_reset_code_failed,
                                it.message ?: getString(R.string.admin_load_error)
                            )
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
        }
    }

    private fun showResetCode(resp: AdminResetCodeResponse) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.admin_reset_code_title, resp.username))
            .setMessage(
                getString(R.string.admin_reset_code_body, resp.expiresInHours, resp.code)
            )
            .setNegativeButton(android.R.string.ok, null)
            .setPositiveButton(R.string.admin_reset_code_copy) { _, _ ->
                val clip = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("Revline reset code", resp.code))
                android.widget.Toast
                    .makeText(requireContext(), R.string.admin_reset_code_copied, android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
            .show()
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
