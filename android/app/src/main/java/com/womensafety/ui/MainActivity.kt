package com.womensafety.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.databinding.ActivityMainBinding
import com.womensafety.ui.alerts.AlertHistoryActivity
import com.womensafety.ui.alerts.EmergencyNumbersActivity
import com.womensafety.ui.alerts.HowItWorksActivity
import com.womensafety.ui.alerts.SafetyTipsActivity
import com.womensafety.ui.alerts.SosCountdownActivity
import com.womensafety.ui.auth.LoginActivity
import com.womensafety.ui.chat.ConversationListActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.womensafety.ui.contacts.TrustedContactsActivity
import com.womensafety.ui.location.LiveTrackingActivity
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPrefManager
    private val holdHandler = Handler(Looper.getMainLooper())
    private var isHolding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = SharedPrefManager.getInstance(this)

        if (!prefs.isLoggedIn()) {
            val intent = Intent(this, SplashActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // If logged in as admin, route directly to Admin Dashboard (unless explicit User View requested)
        if (prefs.getUserRole() == com.womensafety.utils.Constants.ROLE_ADMIN &&
            !intent.getBooleanExtra("stay_in_user_view", false)) {
            val adminIntent = Intent(this, com.womensafety.ui.admin.AdminDashboardActivity::class.java)
            adminIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(adminIntent)
            finish()
            return
        }

        // If logged in as volunteer, route directly to Volunteer Dashboard (unless explicit User View requested)
        if (prefs.getUserRole() == com.womensafety.utils.Constants.ROLE_VOLUNTEER &&
            !intent.getBooleanExtra("stay_in_user_view", false)) {
            val volIntent = Intent(this, com.womensafety.ui.volunteer.VolunteerDashboardActivity::class.java)
            volIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(volIntent)
            finish()
            return
        }

        // If logged in as guardian or friend, route directly to Guardian Protective Shield Dashboard
        if ((prefs.getUserRole() == com.womensafety.utils.Constants.ROLE_GUARDIAN ||
             prefs.getUserRole() == com.womensafety.utils.Constants.ROLE_FRIEND) &&
            !intent.getBooleanExtra("stay_in_user_view", false)) {
            val guardianIntent = Intent(this, com.womensafety.ui.guardian.GuardianDashboardActivity::class.java)
            guardianIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(guardianIntent)
            finish()
            return
        }


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUserHeader()
        setupSosButton()
        setupQuickActionCards()
        setupBottomNav()
        startPulsingAnimation()
        checkAndRequestEmergencyPermissions()

        // Register FCM device token with backend
        com.womensafety.services.SafetyFirebaseMessagingService.registerTokenWithBackend(this)

        if (intent.getBooleanExtra("action_respond_safe", false)) {
            Toast.makeText(this, "Check-In Confirmed: You are marked safe! ✅", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestEmergencyPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CALL_PHONE)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }
    }

    private fun setupUserHeader() {
        val username = prefs.getUserName().takeIf { !it.isNullOrBlank() } ?: "SafeHer"
        binding.tvUserGreeting.text = "Hello, ${username.replaceFirstChar { it.uppercase() }}"

        binding.btnNotifications.setOnClickListener {
            startActivity(Intent(this, AlertHistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, com.womensafety.ui.profile.ProfileActivity::class.java))
        }

        binding.cardLocationPill.setOnClickListener {
            startActivity(Intent(this, LiveTrackingActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun startPulsingAnimation() {
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_glow)
        binding.ringSosOuter.startAnimation(pulse)
        binding.ringSosMid.startAnimation(pulse)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSosButton() {
        // Press & Hold gesture implementation
        binding.btnSos.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHolding = true
                    triggerHaptic(80)
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start()

                    holdHandler.postDelayed({
                        if (isHolding) {
                            triggerHaptic(300)
                            launchSosCountdown()
                        }
                    }, 1000) // 1 second hold triggers SOS
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isHolding) {
                        isHolding = false
                        holdHandler.removeCallbacksAndMessages(null)
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun launchSosCountdown() {
        val intent = Intent(this, SosCountdownActivity::class.java).apply {
            putExtra(SosCountdownActivity.EXTRA_TRIGGER_TYPE, "manual")
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun triggerHaptic(durationMs: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(durationMs)
            }
        }
    }

    private fun setupQuickActionCards() {
        // 1. Emergency Contacts (Pink)
        binding.cardEmergencyContacts.setOnClickListener {
            startActivity(Intent(this, TrustedContactsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // 2. Live Location (Cyan)
        binding.cardLiveLocation.setOnClickListener {
            startActivity(Intent(this, LiveTrackingActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // 3. Nearby Help (Green) -> Volunteer Network (Phase 5)
        binding.cardNearbyHelp.setOnClickListener {
            startActivity(Intent(this, VolunteerNetworkActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // 4. Call 112 (Blue) -> Helpline Directory (Phase 5)
        binding.cardCall112.setOnClickListener {
            startActivity(Intent(this, HelplineDirectoryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // 5. Safety Tips (Purple)
        binding.cardSafetyTips.setOnClickListener {
            startActivity(Intent(this, SafetyTipsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // 6. How SafeHer Works (Orange)
        binding.cardAlertHistory.setOnClickListener {
            startActivity(Intent(this, HowItWorksActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Phase 4: AI Safety Suite
        binding.cardAiMonitor.setOnClickListener {
            startActivity(Intent(this, AiSafetyMonitorActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.cardSafeJourney.setOnClickListener {
            startActivity(Intent(this, SafeJourneyActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.cardDangerZones.setOnClickListener {
            startActivity(Intent(this, DangerZoneHeatmapActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.cardFakeCall.setOnClickListener {
            startActivity(Intent(this, FakeCallActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.cardStealthMode.setOnClickListener {
            startActivity(Intent(this, StealthDisguiseActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }


    override fun onResume() {
        super.onResume()
        updateUnreadChatBadge()
    }

    private fun updateUnreadChatBadge() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@MainActivity).getConversations()
                if (resp.isSuccessful && resp.body() != null) {
                    val unreadTotal = resp.body()!!.sumOf { it.unreadCount }
                    val badge = binding.bottomNavigation.getOrCreateBadge(R.id.nav_chat)
                    if (unreadTotal > 0) {
                        badge.number = unreadTotal
                        badge.isVisible = true
                        badge.backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.primary)
                    } else {
                        badge.isVisible = false
                    }
                }
            } catch (_: Exception) {
                // Keep badge state if offline
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_map -> {
                    startActivity(Intent(this, LiveTrackingActivity::class.java))
                    true
                }
                R.id.nav_alerts -> {
                    startActivity(Intent(this, AlertHistoryActivity::class.java))
                    true
                }
                R.id.nav_chat -> {
                    startActivity(Intent(this, ConversationListActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, com.womensafety.ui.profile.ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
}
