package com.revline.tracker

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.Comment
import com.revline.tracker.databinding.ActivityCommentsBinding
import com.revline.tracker.ui.CommentAdapter
import kotlinx.coroutines.launch

/** Comments on a single server trip. Reusable for own trips and others'. */
class CommentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommentsBinding
    private lateinit var sync: SyncRepository
    private lateinit var adapter: CommentAdapter
    private lateinit var tripId: String
    private var comments = listOf<Comment>()
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommentsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sync = SyncRepository.getInstance(this)
        tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: run { finish(); return }

        binding.backButton.setOnClickListener { finish() }

        adapter = CommentAdapter(onLongPressMine = { confirmDelete(it) })
        binding.commentsList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.commentsList.adapter = adapter

        // Posting requires an account.
        if (!sync.isLoggedIn) {
            binding.commentInput.isEnabled = false
            binding.commentInput.hint = getString(R.string.comment_sign_in)
            binding.sendButton.isEnabled = false
        }

        binding.sendButton.setOnClickListener { send() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            sync.getComments(tripId).onSuccess { submit(it) }
        }
    }

    private fun submit(list: List<Comment>) {
        comments = list
        adapter.submitList(list)
        binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (list.isNotEmpty()) binding.commentsList.scrollToPosition(list.size - 1)
    }

    private fun send() {
        if (sending) return
        val text = binding.commentInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        sending = true
        binding.sendButton.isEnabled = false
        lifecycleScope.launch {
            sync.postComment(tripId, text).onSuccess { c ->
                submit(comments + c)
                binding.commentInput.text?.clear()
                hideKeyboard()
            }.onFailure {
                Toast.makeText(this@CommentsActivity, R.string.comment_failed, Toast.LENGTH_SHORT).show()
            }
            sending = false
            binding.sendButton.isEnabled = true
        }
    }

    private fun confirmDelete(c: Comment) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.comment_delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.admin_reject) { _, _ -> doDelete(c) }
            .show()
    }

    private fun doDelete(c: Comment) {
        lifecycleScope.launch {
            sync.deleteComment(tripId, c.id).onSuccess {
                submit(comments.filterNot { it.id == c.id })
            }.onFailure {
                Toast.makeText(this@CommentsActivity, R.string.comment_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.commentInput.windowToken, 0)
    }

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }
}
