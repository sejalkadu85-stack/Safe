package com.womensafety.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.womensafety.R
import com.womensafety.databinding.ActivitySplashBinding
import com.womensafety.ui.auth.OnboardingActivity
import com.womensafety.utils.SharedPrefManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var prefs: SharedPrefManager
    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = SharedPrefManager.getInstance(this)

        // If user already logged in, navigate directly to Home Dashboard
        if (prefs.isLoggedIn()) {
            navigateToHome()
            return
        }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Play animations for SafeHer logo and brand title
        val logoAnim = AnimationUtils.loadAnimation(this, R.anim.scale_up_bounce)
        val fadeAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.ivLogo.startAnimation(logoAnim)
        binding.llCenterBrand.startAnimation(fadeAnim)
        binding.tvMottoBottom.startAnimation(fadeAnim)

        // Allow instant tap to skip splash delay
        binding.splashRoot.setOnClickListener {
            proceedToNext()
        }

        // Automatic transition after 2.2 seconds
        handler.postDelayed({
            proceedToNext()
        }, 2200)
    }

    private fun proceedToNext() {
        if (hasNavigated) return
        hasNavigated = true
        handler.removeCallbacksAndMessages(null)

        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    private fun navigateToHome() {
        val targetClass = when (prefs.getUserRole()) {
            com.womensafety.utils.Constants.ROLE_ADMIN -> com.womensafety.ui.admin.AdminDashboardActivity::class.java
            com.womensafety.utils.Constants.ROLE_VOLUNTEER -> com.womensafety.ui.volunteer.VolunteerDashboardActivity::class.java
            com.womensafety.utils.Constants.ROLE_GUARDIAN, com.womensafety.utils.Constants.ROLE_FRIEND -> com.womensafety.ui.guardian.GuardianDashboardActivity::class.java
            else -> MainActivity::class.java
        }

        val intent = Intent(this, targetClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
