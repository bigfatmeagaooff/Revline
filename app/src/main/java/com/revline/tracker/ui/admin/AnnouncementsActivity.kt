package com.revline.tracker.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.revline.tracker.R
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.Announcement
import com.revline.tracker.data.remote.AnnouncementRequest
import com.revline.tracker.databinding.ActivityAnnouncementsBinding
import com.revline.tracker.databinding.DialogComposeAnnouncementBinding
import com.revline.tracker.databinding.ItemAnnouncementBinding
import com.revline.tracker.util.EdgeToEdge
import kotlinx.coroutines.launch

/** Admin: compose and manage app-wide announcements. */
class AnnouncementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnnouncementsBinding
    private lateinit var sync: SyncRepository
    private lateinit var adapter: AnnouncementAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnouncementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(binding.root)
        sync = SyncRepository.getInstance(this)

        adapter = AnnouncementAdapter(
            onToggle = { a, active -> setActive(a, active) },
            onDelete = { a -> confirmDelete(a) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        binding.newButton.setOnClickListener { compose() }
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            sync.adminAnnouncements()
                .onSuccess {
                    adapter.submitList(it)
                    binding.emptyState.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                    binding.list.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
                }
                .onFailure { toast(it.message ?: getString(R.string.admin_load_error)) }
        }
    }

    private fun setActive(a: Announcement, active: Boolean) {
        lifecycleScope.launch {
            sync.setAnnouncementActive(a.id, active).onFailure {
                toast(it.message ?: getString(R.string.admin_load_error)); load()
            }
        }
    }

    private fun confirmDelete(a: Announcement) {
        MaterialAlertDialogBuilder(this)
            .setTitle(a.title)
            .setMessage(R.string.announcement_delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    sync.deleteAnnouncement(a.id)
                        .onSuccess { load() }
                        .onFailure { toast(it.message ?: getString(R.string.admin_load_error)) }
                }
            }
            .show()
    }

    private fun compose() {
        val d = DialogComposeAnnouncementBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.announcement_new)
            .setView(d.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.announcement_post, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val title = d.titleInput.text?.toString()?.trim().orEmpty()
                val body = d.bodyInput.text?.toString()?.trim().orEmpty()
                if (title.isEmpty()) { d.titleLayout.error = getString(R.string.error_fill_all_fields); return@setOnClickListener }
                if (body.isEmpty()) { d.bodyLayout.error = getString(R.string.error_fill_all_fields); return@setOnClickListener }
                d.titleLayout.error = null; d.bodyLayout.error = null

                val kind = when (d.kindGroup.checkedRadioButtonId) {
                    R.id.kindEvent -> "event"; R.id.kindUpdate -> "update"; else -> "info"
                }
                val gate = when (d.gateGroup.checkedRadioButtonId) {
                    R.id.gateTimer -> "timer"; R.id.gateBlocking -> "blocking"; else -> "dismissible"
                }
                val req = AnnouncementRequest(
                    title = title,
                    body = body,
                    kind = kind,
                    gate = gate,
                    active = true,
                    minVersionCode = d.minVersionInput.text?.toString()?.trim()?.toIntOrNull(),
                    actionLabel = d.actionLabelInput.text?.toString()?.trim()?.ifBlank { null },
                    actionUrl = d.actionUrlInput.text?.toString()?.trim()?.ifBlank { null },
                    endsAt = null
                )
                lifecycleScope.launch {
                    sync.createAnnouncement(req)
                        .onSuccess { dialog.dismiss(); load() }
                        .onFailure { toast(it.message ?: getString(R.string.admin_load_error)) }
                }
            }
        }
        dialog.show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

private class AnnouncementAdapter(
    val onToggle: (Announcement, Boolean) -> Unit,
    val onDelete: (Announcement) -> Unit
) : ListAdapter<Announcement, AnnouncementAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAnnouncementBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemAnnouncementBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(a: Announcement) {
            b.kindLabel.text = a.kind.uppercase()
            b.title.text = a.title
            b.body.text = a.body
            val bits = buildList {
                if (a.gate != "dismissible") add("${a.gate.uppercase()} GATE")
                a.minVersionCode?.let { add("MIN v$it") }
                add(itemView.context.getString(R.string.announcement_longpress_delete))
            }
            b.meta.text = bits.joinToString("  ·  ")
            b.activeSwitch.setOnCheckedChangeListener(null)
            b.activeSwitch.isChecked = a.active
            b.activeSwitch.setOnCheckedChangeListener { _, checked -> onToggle(a, checked) }
            b.root.setOnLongClickListener { onDelete(a); true }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Announcement>() {
            override fun areItemsTheSame(a: Announcement, b: Announcement) = a.id == b.id
            override fun areContentsTheSame(a: Announcement, b: Announcement) = a == b
        }
    }
}
