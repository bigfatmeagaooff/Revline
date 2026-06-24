package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivityProfileBinding
import com.revline.tracker.databinding.CellStatBinding
import com.revline.tracker.ui.AdminDashboardActivity
import com.revline.tracker.util.CarProfile
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Profile: avatar + stats header, My Car, account actions, and a conditional admin entry. */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var sync: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sync = SyncRepository.getInstance(this)

        val car = CarProfile.load(this)
        binding.makeInput.setText(car.make.orEmpty())
        binding.modelInput.setText(car.model.orEmpty())
        binding.yearInput.setText(car.year?.toString().orEmpty())

        binding.saveCarButton.setOnClickListener { saveCar() }
        binding.loginButton.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        binding.registerButton.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        binding.adminButton.setOnClickListener { startActivity(Intent(this, AdminDashboardActivity::class.java)) }
        binding.logoutButton.setOnClickListener {
            lifecycleScope.launch {
                sync.logout()
                refreshAccount()
                Toast.makeText(this@ProfileActivity, R.string.logged_out, Toast.LENGTH_SHORT).show()
            }
        }

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
            binding.avatar.text = name.firstOrNull()?.uppercase(Locale.getDefault()) ?: "?"
            binding.username.text = name
            binding.userEmail.text = sync.userEmail.orEmpty()
            binding.userEmail.visibility = View.VISIBLE
            binding.statsRow.visibility = View.VISIBLE
            binding.loggedOutButtons.visibility = View.GONE
            binding.logoutButton.visibility = View.VISIBLE
            binding.adminButton.visibility = if (sync.isAdmin) View.VISIBLE else View.GONE
            loadStats()
        } else {
            binding.avatar.text = "?"
            binding.username.text = getString(R.string.not_signed_in)
            binding.userEmail.visibility = View.GONE
            binding.statsRow.visibility = View.GONE
            binding.loggedOutButtons.visibility = View.VISIBLE
            binding.logoutButton.visibility = View.GONE
            binding.adminButton.visibility = View.GONE
        }
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
        CarProfile.save(this, make.ifBlank { null }, model.ifBlank { null }, year)
        Toast.makeText(this, R.string.car_saved, Toast.LENGTH_SHORT).show()
    }
}
