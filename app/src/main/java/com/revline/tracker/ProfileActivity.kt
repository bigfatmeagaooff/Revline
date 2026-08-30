package com.revline.tracker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.service.AutoDetectManager
import com.revline.tracker.util.AppSettings
import com.revline.tracker.util.Avatars
import com.revline.tracker.databinding.ActivityProfileBinding
import com.revline.tracker.databinding.CellStatBinding
import com.revline.tracker.ui.AdminDashboardActivity
import com.revline.tracker.ui.HowItWorksActivity
import com.revline.tracker.util.CarProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import com.revline.tracker.util.EdgeToEdge

/** Profile: avatar + stats header, My Car, account actions, and a conditional admin entry. */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var sync: SyncRepository

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) uploadAvatar(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(binding.root)
        sync = SyncRepository.getInstance(this)

        binding.backButton.setOnClickListener { finish() }
        binding.avatarFrame.setOnClickListener { if (sync.isLoggedIn) choosePhotoAction() }
        binding.changePhoto.setOnClickListener { choosePhotoAction() }

        // The account car (when signed in) is the source of truth; fall back to the
        // local profile for signed-out users.
        val local = CarProfile.load(this)
        binding.makeInput.setText((sync.carMake ?: local.make).orEmpty())
        binding.modelInput.setText((sync.carModel ?: local.model).orEmpty())
        binding.yearInput.setText(((sync.carYear ?: local.year)?.toString()).orEmpty())

        binding.versionLabel.text = getString(
            R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
        )

        binding.saveCarButton.setOnClickListener { saveCar() }
        binding.loginButton.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        binding.registerButton.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        binding.adminButton.setOnClickListener { startActivity(Intent(this, AdminDashboardActivity::class.java)) }
        binding.howItWorksButton.setOnClickListener { startActivity(Intent(this, HowItWorksActivity::class.java)) }
        binding.findPeople.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
        binding.logoutButton.setOnClickListener {
            lifecycleScope.launch {
                sync.logout()
                refreshAccount()
                Toast.makeText(this@ProfileActivity, R.string.logged_out, Toast.LENGTH_SHORT).show()
            }
        }

        setUpAutoDetect()

        // Stat cell labels (set once)
        binding.cellDrives.statLabel.text = getString(R.string.profile_drives)
        binding.cellTopSpeed.statLabel.text = getString(R.string.profile_top)
        binding.cellBestKm.statLabel.text = getString(R.string.profile_best)
    }

    override fun onResume() {
        super.onResume()
        refreshAccount()
    }

    private fun refreshAccount() {
        val loggedIn = sync.isLoggedIn
        if (loggedIn) {
            val name = sync.username.orEmpty()
            Avatars.bind(binding.avatar, binding.avatarImage, sync.avatarUrl, name)
            binding.changePhoto.visibility = View.VISIBLE
            binding.username.text = name
            binding.userEmail.text = sync.userEmail.orEmpty()
            binding.userEmail.visibility = View.VISIBLE
            binding.statsRow.visibility = View.VISIBLE
            binding.loggedOutButtons.visibility = View.GONE
            binding.logoutButton.visibility = View.VISIBLE
            binding.adminButton.visibility = if (sync.isAdmin) View.VISIBLE else View.GONE
            binding.findPeople.visibility = View.VISIBLE
            loadStats()
            loadFollowCounts()
        } else {
            Avatars.bind(binding.avatar, binding.avatarImage, null, null)
            binding.changePhoto.visibility = View.GONE
            binding.username.text = getString(R.string.not_signed_in)
            binding.userEmail.visibility = View.GONE
            binding.statsRow.visibility = View.GONE
            binding.followsRow.visibility = View.GONE
            binding.findPeople.visibility = View.GONE
            binding.loggedOutButtons.visibility = View.VISIBLE
            binding.logoutButton.visibility = View.GONE
            binding.adminButton.visibility = View.GONE
        }
    }

    // --- Profile picture ---

    private fun choosePhotoAction() {
        if (!sync.isLoggedIn) return
        if (sync.avatarUrl == null) {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_photo)
            .setItems(
                arrayOf(getString(R.string.photo_choose_new), getString(R.string.photo_remove))
            ) { _, which ->
                if (which == 0) {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } else {
                    removeAvatar()
                }
            }
            .show()
    }

    private fun uploadAvatar(uri: Uri) {
        binding.changePhoto.isEnabled = false
        binding.changePhoto.text = getString(R.string.photo_uploading)
        lifecycleScope.launch {
            val encoded = withContext(Dispatchers.IO) { Avatars.encodeForUpload(this@ProfileActivity, uri) }
            val result = if (encoded == null) {
                Result.failure(Exception(getString(R.string.photo_read_failed)))
            } else {
                sync.uploadAvatar(encoded)
            }
            binding.changePhoto.isEnabled = true
            binding.changePhoto.text = getString(R.string.change_photo)
            result.onSuccess { refreshAccount() }
                .onFailure {
                    Toast.makeText(
                        this@ProfileActivity,
                        it.message ?: getString(R.string.photo_upload_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun removeAvatar() {
        lifecycleScope.launch {
            sync.removeAvatar()
                .onSuccess { refreshAccount() }
                .onFailure {
                    Toast.makeText(this@ProfileActivity, it.message, Toast.LENGTH_LONG).show()
                }
        }
    }

    // --- Automatic trip detection ---

    /**
     * Auto-detect needs physical-activity and always-on location access. The toggle only
     * sticks once those are granted; otherwise it snaps back off with an explanation and
     * the app keeps working exactly as before (manual start/stop).
     */
    private fun setUpAutoDetect() {
        binding.autoDetectSwitch.isChecked = AppSettings.isAutoDetectEnabled(this)
        renderAutoDetectNote()
        binding.autoDetectSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                AppSettings.setAutoDetectEnabled(this, false)
                AutoDetectManager.stop(this)
                renderAutoDetectNote()
                return@setOnCheckedChangeListener
            }
            if (AutoDetectManager.hasPermissions(this)) {
                enableAutoDetect()
            } else {
                requestAutoDetectPermissions()
            }
        }
    }

    private fun enableAutoDetect() {
        if (!AutoDetectManager.start(this)) {
            // Permissions looked fine but registration failed — don't claim it's on.
            binding.autoDetectSwitch.isChecked = false
            AppSettings.setAutoDetectEnabled(this, false)
            renderAutoDetectNote()
            return
        }
        AppSettings.setAutoDetectEnabled(this, true)
        renderAutoDetectNote()
        Toast.makeText(this, R.string.auto_detect_on, Toast.LENGTH_SHORT).show()
    }

    private fun requestAutoDetectPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            needed += Manifest.permission.ACTIVITY_RECOGNITION
            needed += Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }
        if (needed.isEmpty()) enableAutoDetect() else autoDetectPermissions.launch(needed.toTypedArray())
    }

    private val autoDetectPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (AutoDetectManager.hasPermissions(this)) {
            enableAutoDetect()
        } else {
            binding.autoDetectSwitch.isChecked = false
            AppSettings.setAutoDetectEnabled(this, false)
            renderAutoDetectNote()
            Toast.makeText(this, R.string.auto_detect_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderAutoDetectNote() {
        val on = AppSettings.isAutoDetectEnabled(this)
        binding.autoDetectNote.setText(
            if (on && !AutoDetectManager.hasPermissions(this)) {
                R.string.auto_detect_needs_permission
            } else {
                R.string.auto_detect_explain
            }
        )
    }

    private fun loadFollowCounts() {
        val myId = sync.currentUserId ?: return
        lifecycleScope.launch {
            sync.getUserProfile(myId).onSuccess { p ->
                binding.followsRow.visibility = View.VISIBLE
                binding.followersCount.text = getString(R.string.count_followers, p.followerCount)
                binding.followingCount.text = getString(R.string.count_following, p.followingCount)
                binding.followersCount.setOnClickListener { openList(myId, UserListActivity.MODE_FOLLOWERS) }
                binding.followingCount.setOnClickListener { openList(myId, UserListActivity.MODE_FOLLOWING) }
            }
        }
    }

    private fun openList(userId: String, mode: String) {
        startActivity(
            Intent(this, UserListActivity::class.java)
                .putExtra(UserListActivity.EXTRA_USER_ID, userId)
                .putExtra(UserListActivity.EXTRA_MODE, mode)
        )
    }

    private fun loadStats() {
        // Placeholder dashes until the server responds.
        setStat(binding.cellDrives, "—", "")
        setStat(binding.cellTopSpeed, "—", "")
        setStat(binding.cellBestKm, "—", "")
        lifecycleScope.launch {
            val stats = sync.getProfileStats() ?: return@launch
            setStat(binding.cellDrives, stats.drives.toString(), "")
            setStat(binding.cellTopSpeed, stats.bestTopSpeedKmh.roundToInt().toString(), "")
            setStat(binding.cellBestKm, String.format(Locale.getDefault(), "%.1f", stats.bestDistanceKm), "")
        }
    }

    private fun setStat(cell: CellStatBinding, number: String, unit: String) {
        cell.statNumber.text = number
        cell.statUnit.text = unit
    }

    private fun saveCar() {
        val make = binding.makeInput.text?.toString()?.trim().orEmpty()
        val model = binding.modelInput.text?.toString()?.trim().orEmpty()
        val year = binding.yearInput.text?.toString()?.trim()?.toIntOrNull()

        // Local save always (used by the share card offline). When signed in, the
        // account car is the source of truth for the leaderboard — push it too.
        CarProfile.save(this, make.ifBlank { null }, model.ifBlank { null }, year)

        if (!sync.isLoggedIn) {
            Toast.makeText(this, R.string.car_saved, Toast.LENGTH_SHORT).show()
            return
        }
        if (make.isEmpty()) { binding.makeLayout.error = getString(R.string.error_car_required); return }
        if (model.isEmpty()) { binding.modelLayout.error = getString(R.string.error_car_required); return }
        binding.makeLayout.error = null
        binding.modelLayout.error = null

        binding.saveCarButton.isEnabled = false
        lifecycleScope.launch {
            val r = sync.updateCar(make, model, year)
            binding.saveCarButton.isEnabled = true
            Toast.makeText(
                this@ProfileActivity,
                if (r.isSuccess) getString(R.string.car_saved)
                else r.exceptionOrNull()?.message ?: getString(R.string.admin_load_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
