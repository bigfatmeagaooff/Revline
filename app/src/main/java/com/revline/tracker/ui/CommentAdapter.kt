package com.revline.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.data.remote.Comment
import com.revline.tracker.databinding.ItemCommentBinding
import java.time.Instant
import java.util.Locale

/** A trip comment: avatar, username, relative timestamp, body. Long-press own to delete. */
class CommentAdapter(
    private val onLongPressMine: (Comment) -> Unit
) : ListAdapter<Comment, CommentAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(c: Comment) {
            binding.avatar.text = c.username.firstOrNull()?.uppercase(Locale.getDefault()) ?: "?"
            binding.username.text = c.username
            binding.body.text = c.body
            binding.timestamp.text = relativeTime(c.createdAt)
            binding.root.setOnLongClickListener {
                if (c.isMine) { onLongPressMine(c); true } else false
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(a: Comment, b: Comment) = a.id == b.id
            override fun areContentsTheSame(a: Comment, b: Comment) = a == b
        }

        /** Short relative time like "now", "5m", "3h", "2d". Falls back to "" on parse failure. */
        fun relativeTime(iso: String?): String {
            val t = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return ""
            val diff = (System.currentTimeMillis() - t).coerceAtLeast(0)
            val mins = diff / 60_000
            val hours = mins / 60
            val days = hours / 24
            return when {
                mins < 1 -> "now"
                mins < 60 -> "${mins}m"
                hours < 24 -> "${hours}h"
                days < 7 -> "${days}d"
                else -> "${days / 7}w"
            }
        }
    }
}
