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
            binding.recentTripsContainer.addView(row)
        }
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
