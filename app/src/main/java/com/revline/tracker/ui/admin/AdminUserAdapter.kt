package com.revline.tracker.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revline.tracker.R
import com.revline.tracker.data.remote.AdminUser
import com.revline.tracker.databinding.ItemAdminUserBinding
import com.revline.tracker.util.RelativeTime

class AdminUserAdapter(
    private val onClick: (AdminUser) -> Unit
) : ListAdapter<AdminUser, AdminUserAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAdminUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemAdminUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(user: AdminUser) {
            val ctx = binding.root.context
            binding.username.text = user.username
            binding.email.text = user.email
            binding.activeDot.visibility = if (user.isActive) View.VISIBLE else View.GONE
            binding.adminBadge.visibility = if (user.isAdmin) View.VISIBLE else View.GONE
            binding.meta.text = ctx.getString(
                R.string.admin_user_meta,
                user.tripCount,
                RelativeTime.date(user.createdAt),
                RelativeTime.lastSeen(user.lastSeen)
            )
            binding.root.setOnClickListener { onClick(user) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AdminUser>() {
            override fun areItemsTheSame(a: AdminUser, b: AdminUser) = a.id == b.id
            override fun areContentsTheSame(a: AdminUser, b: AdminUser) = a == b
        }
    }
}
