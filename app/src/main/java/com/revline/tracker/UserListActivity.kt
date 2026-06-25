package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.UserSummary
import com.revline.tracker.databinding.ActivityUserListBinding
import com.revline.tracker.ui.UserAdapter
import kotlinx.coroutines.launch

/** Followers / following list for a user. */
class UserListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserListBinding
    private lateinit var sync: SyncRepository
    private lateinit var adapter: UserAdapter
    private var results = listOf<UserSummary>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sync = SyncRepository.getInstance(this)

        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: run { finish(); return }
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FOLLOWERS
        binding.title.setText(if (mode == MODE_FOLLOWERS) R.string.followers_title else R.string.following_title)
        binding.backButton.setOnClickListener { finish() }

        adapter = UserAdapter(
            currentUserId = sync.currentUserId,
            onClick = { openProfile(it) },
            onToggleFollow = { toggleFollow(it) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        lifecycleScope.launch {
            val result = if (mode == MODE_FOLLOWERS) sync.getFollowers(userId) else sync.getFollowing(userId)
            result.onSuccess { submit(it) }.onFailure { submit(emptyList()) }
        }
    }

    private fun submit(users: List<UserSummary>) {
        results = users
        adapter.submitList(users)
        binding.emptyState.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleFollow(user: UserSummary) {
        lifecycleScope.launch {
            val result = if (user.isFollowing) sync.unfollowUser(user.id) else sync.followUser(user.id)
            result.onSuccess { nowFollowing ->
                results = results.map { if (it.id == user.id) it.copy(isFollowing = nowFollowing) else it }
                adapter.submitList(results)
            }
        }
    }

    private fun openProfile(user: UserSummary) {
        startActivity(
            Intent(this, UserProfileActivity::class.java)
                .putExtra(UserProfileActivity.EXTRA_USER_ID, user.id)
        )
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_MODE = "extra_mode"
        const val MODE_FOLLOWERS = "followers"
        const val MODE_FOLLOWING = "following"
    }
}
