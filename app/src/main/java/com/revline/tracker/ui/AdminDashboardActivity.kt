package com.revline.tracker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.revline.tracker.R
import com.revline.tracker.databinding.ActivityAdminDashboardBinding
import com.revline.tracker.ui.admin.FlaggedFragment
import com.revline.tracker.ui.admin.OverviewFragment
import com.revline.tracker.ui.admin.TripsFragment
import com.revline.tracker.ui.admin.UsersFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Admin-only tabbed dashboard (Overview / Users / Trips / Flagged), replacing the
 * Phase 3.1 single-screen AdminActivity. Reachable only from the conditional button in
 * ProfileActivity; the Flagged tab keeps its own 403 → finish guard.
 */
class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding

    // Drives the Trips tab's user filter; set when a user is tapped in the Users tab.
    private val _userFilter = MutableStateFlow<String?>(null)
    val userFilter: StateFlow<String?> = _userFilter.asStateFlow()

    private val tabTitles by lazy {
        listOf(
            getString(R.string.tab_overview),
            getString(R.string.tab_users),
            getString(R.string.tab_trips),
            getString(R.string.tab_flagged)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 4
            override fun createFragment(position: Int) = when (position) {
                0 -> OverviewFragment()
                1 -> UsersFragment()
                2 -> TripsFragment()
                else -> FlaggedFragment()
            }
        }
        // Keep all tabs alive so the user→trips filter survives tab switches.
        binding.pager.offscreenPageLimit = 3

        TabLayoutMediator(binding.tabs, binding.pager) { tab, pos ->
            tab.text = tabTitles[pos]
        }.attach()
    }

    /** Called from the Users tab: filter Trips to this user and jump to that tab. */
    fun openUserTrips(userId: String) {
        _userFilter.value = userId
        binding.pager.currentItem = 2
    }

    fun clearUserFilter() {
        _userFilter.value = null
    }
}
