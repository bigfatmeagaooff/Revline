package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivityProfileBinding
import com.revline.tracker.ui.AdminDashboardActivity
import com.revline.tracker.util.CarProfile
import kotlinx.coroutines.launch

/**
 * Basic profile: the account section (login/register/logout) and a "My Car" section
 * (three strings stored locally, sent with trip uploads). Not the future Cars table.
 */
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
        binding.loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.logoutButton.setOnClickListener {
            lifecycleScope.launch {
                sync.logout()
                refreshAccount()
                Toast.makeText(this@ProfileActivity, R.string.logged_out, Toast.LENGTH_SHORT).show()
            }
        }
        binding.adminButton.setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccount()
    }

    private fun refreshAccount() {
        if (sync.isLoggedIn) {
            binding.accountStatus.text = getString(R.string.signed_in_as, sync.username ?: "")
            binding.loggedOutButtons.visibility = View.GONE
            binding.logoutButton.visibility = View.VISIBLE
            // Admin entry point only appears for admin accounts — invisible to everyone else.
            binding.adminButton.visibility = if (sync.isAdmin) View.VISIBLE else View.GONE
        } else {
            binding.accountStatus.text = getString(R.string.not_signed_in)
            binding.loggedOutButtons.visibility = View.VISIBLE
            binding.logoutButton.visibility = View.GONE
            binding.adminButton.visibility = View.GONE
        }
    }

    private fun saveCar() {
        val make = binding.makeInput.text?.toString()?.trim().orEmpty()
        val model = binding.modelInput.text?.toString()?.trim().orEmpty()
        val year = binding.yearInput.text?.toString()?.trim()?.toIntOrNull()
        CarProfile.save(this, make.ifBlank { null }, model.ifBlank { null }, year)
        Toast.makeText(this, R.string.car_saved, Toast.LENGTH_SHORT).show()
    }
}
