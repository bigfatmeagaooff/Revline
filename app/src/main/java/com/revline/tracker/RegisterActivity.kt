package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.revline.tracker.data.AuthOutcome
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch
import com.revline.tracker.util.EdgeToEdge

/** Email/password/username registration. Finishes back to the caller on success. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sync: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(binding.root)
        sync = SyncRepository.getInstance(this)

        binding.registerButton.setOnClickListener { submit() }
        binding.goToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun submit() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val carMake = binding.makeInput.text?.toString()?.trim().orEmpty()
        val carModel = binding.modelInput.text?.toString()?.trim().orEmpty()
        val carYear = binding.yearInput.text?.toString()?.trim()?.toIntOrNull()

        if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.error_fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 8) {
            binding.passwordLayout.error = getString(R.string.error_password_length)
            return
        }
        binding.passwordLayout.error = null
        binding.makeLayout.error = null
        binding.modelLayout.error = null
        if (carMake.isEmpty()) {
            binding.makeLayout.error = getString(R.string.error_car_required)
            return
        }
        if (carModel.isEmpty()) {
            binding.modelLayout.error = getString(R.string.error_car_required)
            return
        }

        setBusy(true)
        lifecycleScope.launch {
            when (val result = sync.register(email, password, username, carMake, carModel, carYear)) {
                is AuthOutcome.Success -> {
                    Toast.makeText(this@RegisterActivity, R.string.register_success, Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@RegisterActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                is AuthOutcome.Error -> {
                    setBusy(false)
                    Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.registerButton.isEnabled = !busy
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
    }
}
