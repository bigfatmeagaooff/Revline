package com.revline.tracker

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.data.remote.PublicProfile
import com.revline.tracker.data.remote.RemoteTripSummary
import com.revline.tracker.databinding.ActivityUserProfileBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Public profile: avatar, stats, follow toggle, and recent trips (read-only). */
class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var sync: SyncRepository
    private lateinit var userId: String
    private var following = false
    private var inFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sync = SyncRepository.getInstance(this)
        userId = intent.getStringExtra(EXTRA_USER_ID) ?: run { finish(); return }

        binding.backButton.setOnClickListener { finish() }
        binding.cellDrives.statLabel.text = getString(R.string.profile_drives)
        binding.cellFollowers.statLabel.text = getString(R.string.profile_followers)
        binding.cellTop.statLabel.text = getString(R.string.profile_top)

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            sync.getUserProfile(userId).onSuccess { bind(it) }
            sync.getUserTrips(userId).onSuccess { bindTrips(it) }
        }
    }

    private fun bind(p: PublicProfile) {
        binding.avatar.text = p.username.firstOrNull()?.uppercase(Locale.getDefault()) ?: "?"
        binding.username.text = p.username
        setStat(binding.cellDrives.statNumber, p.tripCount.toString())
        setStat(binding.cellFollowers.statNumber, p.followerCount.toString())
        setStat(binding.cellTop.statNumber, p.bestTopSpeedKmh.roundToInt().toString())

        if (p.isMe) {
            binding.followButton.visibility = View.GONE
        } else {
            binding.followButton.visibility = View.VISIBLE
            following = p.isFollowing
            renderFollowButton()
            binding.followButton.setOnClickListener { onFollowClicked() }
        }
    }

    private fun renderFollowButton() {
        val ctx = this
        if (following) {
            binding.followButton.text = getString(R.string.following)
            binding.followButton.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bg_elevated))
            binding.followButton.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        } else {
            binding.followButton.text = getString(R.string.follow)
            binding.followButton.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.accent_red))
            binding.followButton.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        }
    }

    private fun onFollowClicked() {
        if (inFlight) return
        if (following) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unfollow_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.unfollow) { _, _ -> doToggle() }
                .show()
        } else {
            doToggle()
        }
    }

    private fun doToggle() {
        inFlight = true
        binding.followButton.isEnabled = false
        lifecycleScope.launch {
            val result = if (following) sync.unfollowUser(userId) else sync.followUser(userId)
            result.onSuccess { following = it; renderFollowButton() }
            inFlight = false
            binding.followButton.isEnabled = true
        }
    }

    private fun bindTrips(trips: List<RemoteTripSummary>) {
        binding.recentTripsContainer.removeAllViews()
        binding.noTrips.visibility = if (trips.isEmpty()) View.VISIBLE else View.GONE
        for (t in trips) {
            val row = layoutInflater.inflate(R.layout.item_remote_trip, binding.recentTripsContainer, false)
            row.findViewById<TextView>(R.id.date).text = formatDate(t.startTime)
            row.findViewById<TextView>(R.id.topSpeed).text =
                t.topSpeedKmh?.roundToInt()?.toString() ?: getString(R.string.value_dash)
            val car = listOfNotNull(t.carYear?.toString(), t.carMake, t.carModel).joinToString(" ")
            row.findViewById<TextView>(R.id.car).text = car.ifBlank { getString(R.string.unknown_car) }
            bindLike(row, t)
            row.findViewById<TextView>(R.id.commentCount).text =
                if (t.commentCount > 0) t.commentCount.toString() else ""
            row.findViewById<View>(R.id.commentRow).setOnClickListener { openComments(t.id) }
            binding.recentTripsContainer.addView(row)
        }
    }

    private fun bindLike(row: View, t: RemoteTripSummary) {
        val heart = row.findViewById<android.widget.ImageView>(R.id.heartIcon)
        val countView = row.findViewById<TextView>(R.id.likeCount)
        val likeRow = row.findViewById<View>(R.id.likeRow)
        var liked = t.liked
        var count = t.likeCount
        var busy = false

        fun render() {
            heart.setImageResource(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
            val tint = if (liked) R.color.accent_red else R.color.text_secondary
            heart.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, tint))
            countView.text = if (count > 0) count.toString() else ""
            countView.setTextColor(
                ContextCompat.getColor(this, if (liked) R.color.text_primary else R.color.text_secondary)
            )
        }
        render()

        likeRow.setOnClickListener {
            if (busy) return@setOnClickListener
            busy = true
            // Optimistic update; revert on failure.
            val wasLiked = liked
            liked = !liked
            count = (count + if (liked) 1 else -1).coerceAtLeast(0)
            render()
            lifecycleScope.launch {
                val result = if (wasLiked) sync.unlikeTrip(t.id) else sync.likeTrip(t.id)
                result.onSuccess {
                    liked = it.liked
                    count = it.likeCount
                    render()
                }.onFailure {
                    liked = wasLiked
                    count = (count + if (wasLiked) 1 else -1).coerceAtLeast(0)
                    render()
                }
                busy = false
            }
        }
    }

    private fun openComments(serverTripId: String) {
        startActivity(
            android.content.Intent(this, CommentsActivity::class.java)
                .putExtra(CommentsActivity.EXTRA_TRIP_ID, serverTripId)
        )
    }

    private fun setStat(view: TextView, value: String) { view.text = value }

    private fun formatDate(iso: String?): String {
        val instant = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "—"
        return DATE_FMT.format(instant.atZone(ZoneId.systemDefault())).uppercase(Locale.getDefault())
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    }
}
