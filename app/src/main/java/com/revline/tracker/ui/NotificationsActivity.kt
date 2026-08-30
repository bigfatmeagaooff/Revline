package com.revline.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.CommentsActivity
import com.revline.tracker.R
import com.revline.tracker.UserProfileActivity
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.Notification
import com.revline.tracker.databinding.ActivityNotificationsBinding
import com.revline.tracker.databinding.ItemNotificationBinding
import com.revline.tracker.util.EdgeToEdge
import com.revline.tracker.util.RelativeTime
import kotlinx.coroutines.launch

/** Follow / like / comment notifications. Opening marks everything read. */
class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var sync: SyncRepository
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(binding.root)
        sync = SyncRepository.getInstance(this)

        adapter = NotificationAdapter { open(it) }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        binding.swipeRefresh.setOnRefreshListener { load(markRead = false) }
        load(markRead = true)
    }

    private fun load(markRead: Boolean) {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val resp = sync.fetchNotifications()
            binding.swipeRefresh.isRefreshing = false
            adapter.submitList(resp.notifications)
            val empty = resp.notifications.isEmpty()
            binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
            binding.list.visibility = if (empty) View.GONE else View.VISIBLE
            if (markRead && resp.unreadCount > 0) sync.markNotificationsRead()
        }
    }

    private fun open(n: Notification) {
        when (n.type) {
            "follow" -> n.actorId?.let {
                startActivity(Intent(this, UserProfileActivity::class.java)
                    .putExtra(UserProfileActivity.EXTRA_USER_ID, it))
            }
            "like", "comment" -> n.tripId?.let {
                startActivity(Intent(this, CommentsActivity::class.java)
                    .putExtra(CommentsActivity.EXTRA_TRIP_ID, it))
            }
        }
    }
}

private class NotificationAdapter(
    val onClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(n: Notification) {
            val ctx = b.root.context
            val who = n.actorUsername ?: ctx.getString(R.string.notif_someone)
            b.icon.text = when (n.type) {
                "follow" -> "+"; "like" -> "♥"; "comment" -> "“"; else -> "•"
            }
            b.text.text = when (n.type) {
                "follow" -> ctx.getString(R.string.notif_follow, who)
                "like" -> ctx.getString(R.string.notif_like, who)
                "comment" -> ctx.getString(
                    R.string.notif_comment, who, n.preview?.trim().orEmpty()
                )
                else -> who
            }
            b.time.text = RelativeTime.compact(n.createdAt).uppercase()
            b.unreadDot.visibility = if (n.read) View.INVISIBLE else View.VISIBLE
            b.root.setOnClickListener { onClick(n) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Notification>() {
            override fun areItemsTheSame(a: Notification, b: Notification) = a.id == b.id
            override fun areContentsTheSame(a: Notification, b: Notification) = a == b
        }
    }
}
