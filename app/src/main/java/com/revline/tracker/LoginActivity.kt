package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.revline.tracker.data.AuthOutcome
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

/** Email/password login. Finishes back to the caller on success. */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sync: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sync = SyncRepository.getInstance(this)

        binding.loginButton.setOnClickListener { submit() }
        binding.goToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun submit() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.error_fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            when (val result = sync.login(email, password)) {
                is AuthOutcome.Success -> {
                    Toast.makeText(this@LoginActivity, R.string.login_success, Toast.LENGTH_SHORT).show()
                    sync.restoreTrips() // Fix 3: bring back this account's trip history
                    finish()
                }
                is AuthOutcome.Error -> {
                    setBusy(false)
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.loginButton.isEnabled = !busy
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
    }
}
