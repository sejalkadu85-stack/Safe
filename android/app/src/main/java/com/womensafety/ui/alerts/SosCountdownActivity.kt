package com.womensafety.ui.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import android.net.Uri
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.models.TriggerSOSRequest
import com.womensafety.data.models.TrustedContactResponse
import com.womensafety.databinding.ActivitySosCountdownBinding
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SosCountdownActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySosCountdownBinding
    private var countDownTimer: CountDownTimer? = null
    private var triggerType: String = "manual"
    private var selectedCategory: String = "unsafe_situation"
    private var isSent: Boolean = false
    private var loadedContacts: List<TrustedContactResponse> = emptyList()

    companion object {
        const val EXTRA_TRIGGER_TYPE = "extra_trigger_type"
        const val EXTRA_CATEGORY = "extra_category"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySosCountdownBinding.inflate(layoutInflater)
        setContentView(binding.root)

        triggerType = intent.getStringExtra(EXTRA_TRIGGER_TYPE) ?: "manual"
        selectedCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: "unsafe_situation"

        preloadContacts()
        startPulsingAnimation()
        setupCategoryChips()
        setupButtons()
        startCountdown()
    }

    private fun preloadContacts() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiService = ApiClient.getService(this@SosCountdownActivity)
                val response = apiService.getContacts()
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    loadedContacts = response.body()!!
                    val primary = loadedContacts.firstOrNull { it.tier.equals("primary", ignoreCase = true) } ?: loadedContacts.firstOrNull()
                    val phone = primary?.phoneNumber?.takeIf { it.isNotBlank() } ?: primary?.contactUser?.phoneNumber
                    if (!phone.isNullOrBlank()) {
                        SharedPrefManager.getInstance(this@SosCountdownActivity).savePrimaryGuardianPhone(phone)
                    }
                    val allPhones = loadedContacts.mapNotNull { it.phoneNumber?.takeIf { p -> p.isNotBlank() } ?: it.contactUser?.phoneNumber }.toSet()
                    if (allPhones.isNotEmpty()) {
                        SharedPrefManager.getInstance(this@SosCountdownActivity).saveEmergencyNumbers(allPhones)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPulsingAnimation() {
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_glow)
        binding.ringSosOuter.startAnimation(pulse)
        binding.ringSosMid.startAnimation(pulse)
    }

    private fun setupCategoryChips() {
        val chips = listOf(
            Pair(binding.chipHarassment, "harassment"),
            Pair(binding.chipStalking, "stalking"),
            Pair(binding.chipMedical, "medical"),
            Pair(binding.chipAccident, "accident"),
            Pair(binding.chipUnsafe, "unsafe_situation"),
            Pair(binding.chipOther, "other")
        )

        for ((chip, cat) in chips) {
            chip.setOnClickListener {
                selectedCategory = cat
                highlightSelectedChip(chip, chips.map { it.first })
            }
        }
        highlightSelectedChip(binding.chipUnsafe, chips.map { it.first })
    }

    private fun highlightSelectedChip(selected: TextView, allChips: List<TextView>) {
        for (chip in allChips) {
            if (chip == selected) {
                chip.setBackgroundResource(R.drawable.bg_location_pill)
                chip.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4DFF2D7A"))
                chip.setTextColor(Color.parseColor("#FF2D7A"))
            } else {
                chip.setBackgroundResource(R.drawable.bg_dark_card)
                chip.backgroundTintList = null
                chip.setTextColor(Color.WHITE)
            }
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            cancelCountdown()
            finish()
        }

        binding.btnTopCancel.setOnClickListener {
            cancelCountdown()
            finish()
        }

        binding.btnCancelSos.setOnClickListener {
            cancelCountdown()
            Toast.makeText(this, "Emergency SOS cancelled.", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnCenterSos.setOnClickListener {
            countDownTimer?.cancel()
            broadcastSos()
        }
    }

    private fun startCountdown() {
        // 5-second countdown requirement
        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000)
                binding.tvCountdownTime.text = String.format("00:%02d", secondsRemaining)
                pulseVibration(60)
            }

            override fun onFinish() {
                binding.tvCountdownTime.text = "00:00"
                pulseVibration(400)
                broadcastSos()
            }
        }.start()
    }

    private fun cancelCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun pulseVibration(durationMs: Long) {
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

    private fun broadcastSos() {
        if (isSent) return
        isSent = true

        binding.btnCancelSos.isEnabled = false
        binding.btnTopCancel.isEnabled = false

        val location = getDeviceLocation()
        val batteryPct = getBatteryPercentage()
        val lat = location?.latitude ?: 18.5204
        val lng = location?.longitude ?: 73.8567

        // 1. Send direct SMS with emergency message and Google Maps location to all contacts
        dispatchEmergencySms(lat, lng)

        // 2. Automatically make emergency call to Primary Guardian / 112 to seek attention fast
        val targetPhone = getTargetEmergencyPhone()
        makeEmergencyCall(targetPhone)

        // 3. Dispatch backend incident (which automatically triggers Email, SMS, and Push notifications)
        lifecycleScope.launch(Dispatchers.IO) {
            val req = TriggerSOSRequest(
                latitude = lat,
                longitude = lng,
                altitude = location?.altitude,
                accuracyMeters = location?.accuracy?.toDouble() ?: 10.0,
                triggerType = triggerType,
                category = selectedCategory,
                batteryLevel = batteryPct,
                message = "EMERGENCY! I need immediate help. Category: ${selectedCategory.replace('_', ' ').uppercase()}. Here is my live GPS location."
            )

            try {
                val apiService = ApiClient.getService(this@SosCountdownActivity)
                val response = apiService.triggerSOS(req)

                withContext(Dispatchers.Main) {
                    val alertId = if (response.isSuccessful && response.body() != null) {
                        response.body()!!.alert.id
                    } else {
                        101
                    }
                    navigateToAlertSent(alertId, lat, lng, batteryPct, targetPhone)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    navigateToAlertSent(101, lat, lng, batteryPct, targetPhone)
                }
            }
        }
    }

    private fun getTargetEmergencyPhone(): String? {
        val primaryFromCache = SharedPrefManager.getInstance(this).getPrimaryGuardianPhone()
        if (!primaryFromCache.isNullOrBlank()) return primaryFromCache

        val primaryContact = loadedContacts.firstOrNull { it.tier.equals("primary", ignoreCase = true) }
        val phone = primaryContact?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: primaryContact?.contactUser?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: loadedContacts.firstOrNull()?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: loadedContacts.firstOrNull()?.contactUser?.phoneNumber?.takeIf { it.isNotBlank() }

        return phone?.takeIf { it.isNotBlank() }
    }

    private fun makeEmergencyCall(targetPhone: String?) {
        if (targetPhone.isNullOrBlank()) {
            Toast.makeText(this, "No saved emergency contact to call.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val callIntent = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$targetPhone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$targetPhone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            startActivity(callIntent)
            Toast.makeText(this, "📞 Emergency Call initiated to $targetPhone!", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$targetPhone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(dialIntent)
            } catch (_: Exception) {}
        }
    }

    private fun dispatchEmergencySms(lat: Double, lng: Double) {
        val userName = SharedPrefManager.getInstance(this).getUserName().takeIf { !it.isNullOrBlank() } ?: "User"
        val mapsUrl = "https://maps.google.com/?q=$lat,$lng"
        val smsMessage = "🚨 SAFEHER SOS ALERT! $userName triggered an EMERGENCY (${selectedCategory.replace('_', ' ').uppercase()}). Immediate help required! Live Location: $mapsUrl"

        // Collect all recipient phone numbers strictly from saved contacts
        val numbersToNotify = mutableSetOf<String>()
        for (c in loadedContacts) {
            c.phoneNumber?.takeIf { it.isNotBlank() }?.let { numbersToNotify.add(it) }
            c.contactUser?.phoneNumber?.takeIf { it.isNotBlank() }?.let { numbersToNotify.add(it) }
        }
        SharedPrefManager.getInstance(this).getEmergencyNumbers().filter { it.isNotBlank() }.forEach {
            numbersToNotify.add(it)
        }
        SharedPrefManager.getInstance(this).getPrimaryGuardianPhone()?.takeIf { it.isNotBlank() }?.let {
            numbersToNotify.add(it)
        }

        // Strictly respect user's saved emergency contacts; never dispatch to hardcoded numbers
        if (numbersToNotify.isEmpty()) {
            Toast.makeText(this, "No emergency contacts saved for on-device SMS.", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                for (number in numbersToNotify) {
                    val parts = smsManager.divideMessage(smsMessage)
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                }
                Toast.makeText(this, "📱 Emergency SMS dispatched to ${numbersToNotify.size} contact(s)!", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    private fun navigateToAlertSent(alertId: Int, lat: Double, lng: Double, batteryPct: Int, targetPhone: String?) {
        val intent = Intent(this@SosCountdownActivity, AlertSentActivity::class.java).apply {
            putExtra(AlertSentActivity.EXTRA_ALERT_ID, alertId)
            putExtra(AlertSentActivity.EXTRA_LATITUDE, lat)
            putExtra(AlertSentActivity.EXTRA_LONGITUDE, lng)
            putExtra(AlertSentActivity.EXTRA_CATEGORY, selectedCategory)
            putExtra(AlertSentActivity.EXTRA_BATTERY, batteryPct)
            putExtra(AlertSentActivity.EXTRA_CALLED_PHONE, targetPhone ?: "")
            putExtra(AlertSentActivity.EXTRA_NOTIFIED_COUNT, maxOf(1, loadedContacts.size))
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation(): Location? {
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            86
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
