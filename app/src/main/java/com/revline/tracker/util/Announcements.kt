package com.revline.tracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.revline.tracker.BuildConfig
import com.revline.tracker.R
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.Announcement
import kotlinx.coroutines.launch

/**
 * Shows the one most important pending announcement on app open. Update gates
 * (an "update" whose minVersionCode is above this build) come first and repeat
 * until the user updates; info/event messages show once, then are acked.
 */
object Announcements {

    private const val PREFS = "revline_announcements"
    private const val KEY_SEEN = "seen_ids"
    private const val TIMER_SECONDS = 5

    fun checkOnOpen(activity: AppCompatActivity) {
        val sync = SyncRepository.getInstance(activity)
        activity.lifecycleScope.launch {
            val list = sync.fetchAnnouncements(BuildConfig.VERSION_CODE)
            if (list.isEmpty() || activity.isFinishing) return@launch
            val seen = seenIds(activity)
            val next = list.firstOrNull { isUpdateGate(it) } // gates win, ignore "seen"
                ?: list.firstOrNull { it.id !in seen }
                ?: return@launch
            show(activity, sync, next)
        }
    }

    private fun isUpdateGate(a: Announcement) =
        a.minVersionCode != null && BuildConfig.VERSION_CODE < a.minVersionCode

    private fun show(activity: AppCompatActivity, sync: SyncRepository, a: Announcement) {
        val gate = isUpdateGate(a)
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(a.title)
            .setMessage(a.body)
            .setCancelable(!gate || a.gate == "dismissible")

        val ack: () -> Unit = {
            markSeen(activity, a.id)
            activity.lifecycleScope.launch { sync.ackAnnouncement(a.id) }
        }

        if (gate) {
            // "Update" opens the link; the second button depends on the gate mode.
            a.actionUrl?.let { url ->
                builder.setPositiveButton(a.actionLabel ?: activity.getString(R.string.announce_update)) { _, _ ->
                    openUrl(activity, url)
                }
            }
            when (a.gate) {
                "blocking" -> { /* no way past — Update only */ }
                "timer" -> builder.setNegativeButton(
                    activity.getString(R.string.announce_continue_wait, TIMER_SECONDS), null
                )
                else -> builder.setNegativeButton(R.string.announce_later, null)
            }
        } else {
            a.actionUrl?.let { url ->
                a.actionLabel?.let { label ->
                    builder.setNeutralButton(label) { _, _ -> openUrl(activity, url); ack() }
                }
            }
            builder.setPositiveButton(R.string.announce_got_it) { _, _ -> ack() }
        }

        val dialog = builder.create()

        if (gate && a.gate == "timer") {
            dialog.setOnShowListener {
                val cont = dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                cont.isEnabled = false
                object : CountDownTimer((TIMER_SECONDS * 1000).toLong(), 1000) {
                    override fun onTick(ms: Long) {
                        cont.text = activity.getString(
                            R.string.announce_continue_wait, (ms / 1000 + 1).toInt()
                        )
                    }
                    override fun onFinish() {
                        cont.text = activity.getString(R.string.announce_continue)
                        cont.isEnabled = true
                        cont.setOnClickListener { dialog.dismiss() }
                    }
                }.start()
            }
        }
        dialog.show()
    }

    private fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun seenIds(c: Context): Set<String> = prefs(c).getStringSet(KEY_SEEN, emptySet()) ?: emptySet()
    private fun markSeen(c: Context, id: String) {
        prefs(c).edit().putStringSet(KEY_SEEN, seenIds(c) + id).apply()
    }
}
