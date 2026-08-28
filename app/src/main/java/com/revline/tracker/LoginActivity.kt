package com.revline.tracker

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.revline.tracker.data.AuthOutcome
import com.revline.tracker.data.SyncRepository
import com.revline.tracker.databinding.ActivityLoginBinding
import com.revline.tracker.databinding.DialogResetPasswordBinding
import kotlinx.coroutines.launch
import com.revline.tracker.util.EdgeToEdge

/** Email/password login. Finishes back to the caller on success. */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sync: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(binding.root)
        sync = SyncRepository.getInstance(this)

        binding.loginButton.setOnClickListener { submit() }
        binding.forgotPassword.setOnClickListener { showResetDialog() }
        binding.goToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
        // The app is fully usable without an account (local tracking) — never dead-end
        // someone at the sign-in screen.
        binding.skipLogin.setOnClickListener { goToMain() }
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
                    goToMain()
                }
                is AuthOutcome.Error -> {
                    setBusy(false)
                    Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Admin-assisted password reset: the user gets a one-time code from an admin, then
     * enters it here with a new password. (A self-service email flow calls the same
     * endpoint later.)
     */
    private fun showResetDialog() {
        val dialogBinding = DialogResetPasswordBinding.inflate(layoutInflater)
        dialogBinding.emailInput.setText(
            binding.emailInput.text?.toString()?.trim().orEmpty()
        )

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_password_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset_password_button, null) // set below to avoid auto-dismiss
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val email = dialogBinding.emailInput.text?.toString()?.trim().orEmpty()
                val code = dialogBinding.codeInput.text?.toString()?.trim().orEmpty()
                val newPassword = dialogBinding.newPasswordInput.text?.toString().orEmpty()

                dialogBinding.emailLayout.error = null
                dialogBinding.codeLayout.error = null
                dialogBinding.newPasswordLayout.error = null

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    dialogBinding.emailLayout.error = getString(R.string.error_fill_all_fields)
                    return@setOnClickListener
                }
                if (code.isEmpty()) {
                    dialogBinding.codeLayout.error = getString(R.string.error_fill_all_fields)
                    return@setOnClickListener
                }
                if (newPassword.length < 8) {
                    dialogBinding.newPasswordLayout.error = getString(R.string.error_password_length)
                    return@setOnClickListener
                }

                val positive = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                positive.isEnabled = false
                lifecycleScope.launch {
                    when (val result = sync.resetPassword(email, code, newPassword)) {
                        is AuthOutcome.Success -> {
                            dialog.dismiss()
                            binding.emailInput.setText(email)
                            binding.passwordInput.text?.clear()
                            Toast.makeText(
                                this@LoginActivity,
                                R.string.reset_password_success,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is AuthOutcome.Error -> {
                            positive.isEnabled = true
                            dialogBinding.codeLayout.error = result.message
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun setBusy(busy: Boolean) {
        binding.loginButton.isEnabled = !busy
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
