package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.UserSummary
import com.revline.tracker.databinding.ActivitySearchBinding
import com.revline.tracker.ui.UserAdapter
import kotlinx.coroutines.launch

/** Debounced username search with inline follow toggles. */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var sync: SyncRepository
    private lateinit var adapter: UserAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var results = listOf<UserSummary>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sync = SyncRepository.getInstance(this)

        binding.backButton.setOnClickListener { finish() }

        adapter = UserAdapter(
            currentUserId = sync.currentUserId,
            onClick = { openProfile(it) },
            onToggleFollow = { toggleFollow(it) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pending?.let { handler.removeCallbacks(it) }
                val q = s?.toString()?.trim().orEmpty()
                pending = Runnable { search(q) }.also { handler.postDelayed(it, 300) }
            }
        })
    }

    private fun search(q: String) {
        if (q.isEmpty()) {
            submit(emptyList())
            return
        }
        lifecycleScope.launch {
            sync.searchUsers(q).onSuccess { submit(it) }.onFailure { submit(emptyList()) }
        }
    }

    private fun submit(users: List<UserSummary>) {
        results = users
        adapter.submitList(users)
        binding.emptyState.visibility =
            if (users.isEmpty() && binding.searchInput.text?.isNotEmpty() == true) View.VISIBLE else View.GONE
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
}
