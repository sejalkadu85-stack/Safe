package com.womensafety.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.womensafety.data.models.SendOtpRequest
import com.womensafety.data.models.VerifyOtpRequest
import com.womensafety.data.repository.AuthRepository
import com.womensafety.databinding.ActivityOtpVerificationBinding
import com.womensafety.ui.MainActivity
import com.womensafety.utils.Resource
import kotlinx.coroutines.launch

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var authRepository: AuthRepository
    private var phoneOrEmail: String = ""

    companion object {
        const val EXTRA_PHONE_OR_EMAIL = "extra_phone_or_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)
        phoneOrEmail = intent.getStringExtra(EXTRA_PHONE_OR_EMAIL) ?: ""

        if (phoneOrEmail.isNotEmpty()) {
            binding.tvSubtitle.text = "We have sent a 6-digit verification code to $phoneOrEmail. Please enter it below."
            // Send initial OTP
            sendOtpCode()
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvResendOtp.setOnClickListener {
            sendOtpCode()
        }

        binding.btnVerify.setOnClickListener {
            verifyOtpCode()
        }
    }

    private fun sendOtpCode() {
        if (phoneOrEmail.isEmpty()) return

        lifecycleScope.launch {
            setLoading(true)
            val result = authRepository.sendOtp(SendOtpRequest(phoneOrEmail, "verification"))
            setLoading(false)

            when (result) {
                is Resource.Success -> {
                    val code = result.data?.debugOtp
                    if (!code.isNullOrEmpty()) {
                        binding.etOtpCode.setText(code)
                        Toast.makeText(this@OtpVerificationActivity, "OTP: $code (Auto-filled)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@OtpVerificationActivity, "Verification code sent!", Toast.LENGTH_SHORT).show()
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(this@OtpVerificationActivity, result.message ?: "Failed to send code", Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> setLoading(true)
            }
        }
    }

    private fun verifyOtpCode() {
        val code = binding.etOtpCode.text.toString().trim()

        if (code.length != 6) {
            binding.etOtpCode.error = "Please enter the 6-digit code"
            binding.etOtpCode.requestFocus()
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            val result = authRepository.verifyOtp(VerifyOtpRequest(phoneOrEmail, code, "verification"))
            setLoading(false)

            when (result) {
                is Resource.Success -> {
                    val prefs = com.womensafety.utils.SharedPrefManager.getInstance(this@OtpVerificationActivity)
                    if (prefs.getAccessToken().isNullOrEmpty()) {
                        prefs.saveAuthTokens("verified_access_${System.currentTimeMillis()}", "verified_refresh")
                    }
                    Toast.makeText(this@OtpVerificationActivity, "Account verified successfully!", Toast.LENGTH_SHORT).show()
                    val targetClass = when (prefs.getUserRole()) {
                        com.womensafety.utils.Constants.ROLE_ADMIN -> com.womensafety.ui.admin.AdminDashboardActivity::class.java
                        com.womensafety.utils.Constants.ROLE_VOLUNTEER -> com.womensafety.ui.volunteer.VolunteerDashboardActivity::class.java
                        com.womensafety.utils.Constants.ROLE_GUARDIAN, com.womensafety.utils.Constants.ROLE_FRIEND -> com.womensafety.ui.guardian.GuardianDashboardActivity::class.java
                        else -> MainActivity::class.java
                    }

                    val intent = Intent(this@OtpVerificationActivity, targetClass).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                is Resource.Error -> {
                    Toast.makeText(this@OtpVerificationActivity, result.message ?: "Invalid OTP code. Please try again.", Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> setLoading(true)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnVerify.isEnabled = !isLoading
        binding.tvResendOtp.isEnabled = !isLoading
    }
}
