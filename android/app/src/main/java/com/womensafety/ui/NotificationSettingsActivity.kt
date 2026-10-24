package com.womensafety.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.womensafety.R
import com.womensafety.databinding.ActivityNotificationSettingsBinding
import com.womensafety.services.SafetyFirebaseMessagingService
import com.womensafety.ui.location.LiveTrackingActivity
import com.womensafety.utils.SharedPrefManager

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding
    private lateinit var prefs: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)

        loadPreferences()
        setupListeners()
    }

    private fun loadPreferences() {
        binding.switchSos.isChecked = prefs.isSosNotificationEnabled()
        binding.switchLocation.isChecked = prefs.isLocationNotificationEnabled()
        binding.switchChat.isChecked = prefs.isChatNotificationEnabled()
        binding.switchContact.isChecked = prefs.isContactNotificationEnabled()
        binding.switchCheckIn.isChecked = prefs.isCheckInNotificationEnabled()
        binding.switchSound.isChecked = prefs.isSoundEnabled()
        binding.switchVibrate.isChecked = prefs.isVibrationEnabled()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.switchSos.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSosNotificationEnabled(isChecked)
        }

        binding.switchLocation.setOnCheckedChangeListener { _, isChecked ->
            prefs.setLocationNotificationEnabled(isChecked)
        }

        binding.switchChat.setOnCheckedChangeListener { _, isChecked ->
            prefs.setChatNotificationEnabled(isChecked)
        }

        binding.switchContact.setOnCheckedChangeListener { _, isChecked ->
            prefs.setContactNotificationEnabled(isChecked)
        }

        binding.switchCheckIn.setOnCheckedChangeListener { _, isChecked ->
            prefs.setCheckInNotificationEnabled(isChecked)
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSoundEnabled(isChecked)
        }

        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.setVibrationEnabled(isChecked)
        }

        binding.btnTestNotification.setOnClickListener {
            triggerSampleSosNotification()
        }
    }

    private fun triggerSampleSosNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = SafetyFirebaseMessagingService.CHANNEL_EMERGENCY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent high-priority life safety and emergency alerts"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(prefs.isVibrationEnabled())
                vibrationPattern = if (prefs.isVibrationEnabled()) longArrayOf(0, 500, 200, 500) else null
            }
            manager.createNotificationChannel(channel)
        }

        val mapIntent = Intent(this, LiveTrackingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_sos_alert", true)
            putExtra("extra_lat", 28.6139)
            putExtra("extra_lng", 77.2090)
        }
        val mapPendingIntent = PendingIntent.getActivity(
            this,
            999,
            mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
        val callPendingIntent = PendingIntent.getActivity(
            this,
            1000,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_safety_shield)
            .setContentTitle("🚨 EMERGENCY SOS ALERT: SafeHer User")
            .setContentText("Emergency SOS triggered near Connaught Place. Tap to open map & dispatch help.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Emergency SOS triggered near Connaught Place (28.6139, 77.2090). Coordinates received from device sensor."))
            .setColor(Color.RED)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(mapPendingIntent, true)
            .setContentIntent(mapPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_location_pin, "View Map 🗺️", mapPendingIntent)
            .addAction(R.drawable.ic_phone_neon, "Call 112 📞", callPendingIntent)

        manager.notify(777, builder.build())
        Toast.makeText(this, "Sample SOS Alert dispatched to notification bar!", Toast.LENGTH_SHORT).show()
    }
}
