package com.revline.tracker.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.R
import com.revline.tracker.data.remote.UserSummary
import com.revline.tracker.databinding.ItemUserBinding
import java.util.Locale

/** User row with avatar, follower count, and a Follow/Following toggle. */
class UserAdapter(
    private val currentUserId: String?,
    private val onClick: (UserSummary) -> Unit,
    private val onToggleFollow: (UserSummary) -> Unit
) : ListAdapter<UserSummary, UserAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: UserSummary) {
            val ctx = binding.root.context
            binding.avatar.text = user.username.firstOrNull()?.uppercase(Locale.getDefault()) ?: "?"
            binding.username.text = user.username
            binding.followerMeta.text =
                ctx.resources.getQuantityString(R.plurals.followers_count, user.followerCount, user.followerCount)

            if (user.id == currentUserId) {
                binding.followButton.visibility = View.GONE
            } else {
                binding.followButton.visibility = View.VISIBLE
                val red = ContextCompat.getColor(ctx, R.color.accent_red)
                val grey = ContextCompat.getColor(ctx, R.color.text_secondary)
                if (user.isFollowing) {
                    binding.followButton.text = ctx.getString(R.string.following)
                    binding.followButton.setTextColor(grey)
                    binding.followButton.strokeColor = ColorStateList.valueOf(grey)
                } else {
                    binding.followButton.text = ctx.getString(R.string.follow)
                    binding.followButton.setTextColor(red)
                    binding.followButton.strokeColor = ColorStateList.valueOf(red)
                }
                binding.followButton.setOnClickListener { onToggleFollow(user) }
            }

            binding.root.setOnClickListener { onClick(user) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserSummary>() {
            override fun areItemsTheSame(a: UserSummary, b: UserSummary) = a.id == b.id
            override fun areContentsTheSame(a: UserSummary, b: UserSummary) = a == b
        }
    }
}
