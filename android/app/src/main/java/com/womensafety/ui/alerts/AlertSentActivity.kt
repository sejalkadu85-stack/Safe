package com.womensafety.ui.alerts

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.api.ApiService
import com.womensafety.data.models.CancelSOSRequest
import com.womensafety.data.models.IncidentTimelineItem
import com.womensafety.databinding.ActivityAlertSentBinding
import com.womensafety.ui.MainActivity
import com.womensafety.ui.chat.IncidentChatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody

class AlertSentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertSentBinding
    private lateinit var apiService: ApiService

    private var alertId: Int = 101
    private var latitude: Double = 18.5204
    private var longitude: Double = 73.8567
    private var category: String = "unsafe_situation"
    private var escalationTier: Int = 1
    private var escalationTimer: CountDownTimer? = null
    private var calledPhone: String = "112"

    companion object {
        const val EXTRA_ALERT_ID = "extra_alert_id"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_NOTIFIED_COUNT = "extra_notified_count"
        const val EXTRA_BATTERY = "extra_battery"
        const val EXTRA_CALLED_PHONE = "extra_called_phone"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertSentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alertId = intent.getIntExtra(EXTRA_ALERT_ID, 101)
        latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 18.5204)
        longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 73.8567)
        category = intent.getStringExtra(EXTRA_CATEGORY) ?: "unsafe_situation"
        calledPhone = intent.getStringExtra(EXTRA_CALLED_PHONE) 
            ?: com.womensafety.utils.SharedPrefManager.getInstance(this).getPrimaryGuardianPhone() 
            ?: "112"

        apiService = ApiClient.getService(this)

        setupUI()
        setupListeners()
        startEscalationCountdown(45)
        loadIncidentStatus()
        startPeriodicPolling()
    }

    private fun setupUI() {
        binding.tvHeaderTitle.text = "Incident Tracking #$alertId"
        binding.tvCategoryPill.text = category.replace("_", " ").uppercase()
        binding.tvLocationCoords.text = String.format("%.4f° N, %.4f° E · Live Coordinates", latitude, longitude)

        binding.tvCallStatus.text = "Emergency Call connected ($calledPhone) for immediate attention"
        binding.btnCallGuardianNow.text = if (calledPhone != "112") "📞 Redial Primary Guardian ($calledPhone)" else "📞 Call Guardian / 112"

        updateLifecycleStepper("open")
        renderDefaultTimeline()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnOpenGoogleMaps.setOnClickListener {
            val uri = Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        binding.btnOpenChatRoom.setOnClickListener {
            openChatActivity()
        }

        binding.btnIncidentChatTop.setOnClickListener {
            openChatActivity()
        }

        binding.btnManualEscalate.setOnClickListener {
            triggerManualEscalation()
        }

        binding.btnCallGuardianNow.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$calledPhone")))
        }

        binding.btnCallPoliceNow.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }

        binding.btnCancelAlert.setOnClickListener {
            confirmCancelAlert()
        }

        setupVoiceRecorder()
    }

    private var voiceTimerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var voiceSecondsElapsed = 0
    private var recordedAudioFile: java.io.File? = null
    private val voiceRecorder by lazy { com.womensafety.utils.VoiceRecorderHelper(this) }
    private val voicePlayer by lazy { com.womensafety.utils.VoicePlayerHelper() }

    private fun setupVoiceRecorder() {
        binding.btnRecordVoiceNote.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 2002
                )
                return@setOnClickListener
            }

            if (!voiceRecorder.isCurrentlyRecording()) {
                // Start Recording
                if (voiceRecorder.startRecording()) {
                    voiceSecondsElapsed = 0
                    binding.tvVoiceRecordTimer.visibility = View.VISIBLE
                    binding.tvVoiceRecordTimer.text = "00:00"
                    binding.tvVoiceRecordStatus.text = "🔴 Recording voice note..."
                    binding.tvVoiceRecordStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
                    binding.btnRecordVoiceNote.text = "⏹ Stop & Upload"
                    binding.btnPlayVoiceNote.visibility = View.GONE

                    voiceTimerHandler.post(object : Runnable {
                        override fun run() {
                            if (voiceRecorder.isCurrentlyRecording()) {
                                voiceSecondsElapsed++
                                val mins = voiceSecondsElapsed / 60
                                val secs = voiceSecondsElapsed % 60
                                binding.tvVoiceRecordTimer.text = String.format("%02d:%02d", mins, secs)
                                voiceTimerHandler.postDelayed(this, 1000)
                            }
                        }
                    })
                } else {
                    Toast.makeText(this, "Could not start audio recorder", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Stop Recording & Upload
                val duration = voiceRecorder.stopRecording()
                recordedAudioFile = voiceRecorder.outputFile
                binding.tvVoiceRecordTimer.visibility = View.GONE

                if (recordedAudioFile != null && recordedAudioFile!!.exists() && recordedAudioFile!!.length() > 0) {
                    binding.btnPlayVoiceNote.visibility = View.VISIBLE
                    uploadVoiceNote(recordedAudioFile!!, duration)
                } else {
                    binding.tvVoiceRecordStatus.text = "Recording cancelled or file empty."
                    binding.btnRecordVoiceNote.text = "🎤 Record Voice Note"
                }
            }
        }

        binding.btnPlayVoiceNote.setOnClickListener {
            recordedAudioFile?.let { file ->
                if (voicePlayer.isPlaying) {
                    voicePlayer.stopAudio()
                    binding.btnPlayVoiceNote.text = "▶ Play"
                } else {
                    binding.btnPlayVoiceNote.text = "⏹ Pause"
                    voicePlayer.playAudio(file.absolutePath) {
                        binding.btnPlayVoiceNote.text = "▶ Play"
                    }
                }
            }
        }
    }

    private fun uploadVoiceNote(file: java.io.File, durationSeconds: Int) {
        binding.pbVoiceUpload.visibility = View.VISIBLE
        binding.tvVoiceRecordStatus.text = "Uploading voice note ($durationSeconds seconds)..."
        binding.tvVoiceRecordStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reqFile = okhttp3.RequestBody.create("audio/m4a".toMediaTypeOrNull(), file)
                val filePart = MultipartBody.Part.createFormData("file", file.name, reqFile)
                val typePart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), "audio")
                val durationPart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), durationSeconds.toString())
                val alertIdPart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), alertId.toString())

                val response = apiService.uploadEvidence(filePart, typePart, durationPart, alertIdPart)

                withContext(Dispatchers.Main) {
                    binding.pbVoiceUpload.visibility = View.GONE
                    if (response.isSuccessful) {
                        binding.tvVoiceRecordStatus.text = "✅ Voice note dispatched! Guardians & Responders notified."
                        binding.btnRecordVoiceNote.text = "🎤 Record New Voice Note"
                        Toast.makeText(this@AlertSentActivity, "🎤 Voice note uploaded & linked to SOS!", Toast.LENGTH_LONG).show()
                        loadIncidentStatus()
                    } else {
                        binding.tvVoiceRecordStatus.text = "⚠️ Voice note saved locally. Upload failed."
                        binding.btnRecordVoiceNote.text = "🎤 Re-try Upload"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.pbVoiceUpload.visibility = View.GONE
                    binding.tvVoiceRecordStatus.text = "⚠️ Saved locally as evidence."
                    binding.btnRecordVoiceNote.text = "🎤 Record New Voice Note"
                }
            }
        }
    }

    private fun openChatActivity() {
        val intent = Intent(this, IncidentChatActivity::class.java).apply {
            putExtra(IncidentChatActivity.EXTRA_ALERT_ID, alertId)
            putExtra(IncidentChatActivity.EXTRA_VICTIM_NAME, "You (Ward)")
            putExtra(IncidentChatActivity.EXTRA_CATEGORY, category)
        }
        startActivity(intent)
    }

    private fun startEscalationCountdown(seconds: Int) {
        escalationTimer?.cancel()
        escalationTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val rem = millisUntilFinished / 1000
                binding.tvEscalationCountdown.text = String.format("00:%02d", rem)
            }

            override fun onFinish() {
                binding.tvEscalationCountdown.text = "00:00"
                triggerManualEscalation()
            }
        }.start()
    }

    private fun triggerManualEscalation() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.escalateSOSIncident(alertId)
                withContext(Dispatchers.Main) {
                    escalationTier = minOf(3, escalationTier + 1)
                    updateEscalationTierUI()
                    loadIncidentStatus()
                    Toast.makeText(this@AlertSentActivity, "⚡ Incident escalated to Tier $escalationTier!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    escalationTier = minOf(3, escalationTier + 1)
                    updateEscalationTierUI()
                }
            }
        }
    }

    private fun updateEscalationTierUI() {
        when (escalationTier) {
            1 -> {
                binding.tvEscalationTierHeader.text = "Tier 1 · Primary Guardian Notified"
                binding.tvEscalationDetail.text = "If unanswered, alert escalates automatically to Secondary Guardians & Friends."
                updateLifecycleStepper("open")
            }
            2 -> {
                binding.tvEscalationTierHeader.text = "Tier 2 · Secondary Contacts & Friends Notified"
                binding.tvEscalationDetail.text = "High priority distress alert sent to all verified friends & secondary guardians."
                updateLifecycleStepper("escalated")
            }
            3 -> {
                binding.tvEscalationTierHeader.text = "Tier 3 · Nearby Verified Volunteers & First Responders"
                binding.tvEscalationDetail.text = "Volunteers within 10km radius alerted to mobilize and assist immediately."
                binding.tvEscalationCountdown.text = "ACTIVE"
                binding.tvEscalationCountdown.setTextColor(Color.parseColor("#00E676"))
                updateLifecycleStepper("escalated")
            }
        }
    }

    private fun updateLifecycleStepper(status: String) {
        val pink = Color.parseColor("#FF2D7A")
        val cyan = Color.parseColor("#00E5FF")
        val green = Color.parseColor("#00E676")
        val muted = Color.parseColor("#5D6882")

        when (status.lowercase()) {
            "open" -> {
                binding.tvStepNum1.backgroundTintList = ColorStateList.valueOf(pink)
                binding.tvStepNum2.backgroundTintList = ColorStateList.valueOf(muted)
                binding.tvStepNum3.backgroundTintList = ColorStateList.valueOf(muted)
                binding.tvStepNum4.backgroundTintList = ColorStateList.valueOf(muted)
                binding.tvStepNum5.backgroundTintList = ColorStateList.valueOf(muted)
            }
            "active" -> {
                binding.tvStepNum1.backgroundTintList = ColorStateList.valueOf(pink)
                binding.tvStepNum2.backgroundTintList = ColorStateList.valueOf(cyan)
                binding.tvStepNum3.backgroundTintList = ColorStateList.valueOf(muted)
                binding.tvStepNum4.backgroundTintList = ColorStateList.valueOf(muted)
                binding.tvStepNum5.backgroundTintList = ColorStateList.valueOf(muted)
            }
            "escalated" -> {
                binding.tvStepNum1.backgroundTintList = ColorStateList.valueOf(pink)
                binding.tvStepNum2.backgroundTintList = ColorStateList.valueOf(cyan)
                binding.tvStepNum3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9100"))
                binding.tvStepNum4.backgroundTintList = ColorStateList.valueOf(muted)
                binding.tvStepNum5.backgroundTintList = ColorStateList.valueOf(muted)
            }
            "resolved" -> {
                binding.tvStepNum1.backgroundTintList = ColorStateList.valueOf(pink)
                binding.tvStepNum2.backgroundTintList = ColorStateList.valueOf(cyan)
                binding.tvStepNum3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9100"))
                binding.tvStepNum4.backgroundTintList = ColorStateList.valueOf(green)
                binding.tvStepNum5.backgroundTintList = ColorStateList.valueOf(muted)
            }
            "closed" -> {
                binding.tvStepNum1.backgroundTintList = ColorStateList.valueOf(pink)
                binding.tvStepNum2.backgroundTintList = ColorStateList.valueOf(cyan)
                binding.tvStepNum3.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9100"))
                binding.tvStepNum4.backgroundTintList = ColorStateList.valueOf(green)
                binding.tvStepNum5.backgroundTintList = ColorStateList.valueOf(green)
            }
        }
    }

    private fun loadIncidentStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getSOSIncidentStatus(alertId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val alert = body.alert
                        escalationTier = alert.escalationTier
                        updateLifecycleStepper(alert.lifecycleStatus ?: "open")
                        renderTimeline(body.timeline ?: emptyList())
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun renderDefaultTimeline() {
        val defaultList = listOf(
            IncidentTimelineItem(1, "open", "User", "SOS Triggered with live GPS coordinates.", "Just now"),
            IncidentTimelineItem(2, "active", "System", "Tier 1: Primary Guardian alerted via SMS & Push notification.", "Just now")
        )
        renderTimeline(defaultList)
    }

    private fun renderTimeline(events: List<IncidentTimelineItem>) {
        binding.llTimelineEntries.removeAllViews()
        for (item in events) {
            val row = TextView(this).apply {
                val timeStr = if (item.timestamp.contains("T")) item.timestamp.substringAfter("T").take(5) else item.timestamp
                text = "• [$timeStr] ${item.actorName ?: "System"}: ${item.note ?: item.status.uppercase()}"
                setTextColor(Color.parseColor("#8F9BB3"))
                textSize = 12f
                setPadding(0, 4, 0, 4)
            }
            binding.llTimelineEntries.addView(row)
        }
    }

    private fun confirmCancelAlert() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Safe Status")
            .setMessage("Are you safe now? This will resolve the active emergency alert and notify all your contacts.")
            .setPositiveButton("I AM SAFE") { _, _ ->
                cancelEmergencyAlert()
            }
            .setNegativeButton("Keep Alert Active", null)
            .show()
    }

    private fun cancelEmergencyAlert() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiService.resolveSOSIncident(
                    alertId,
                    mapOf("note" to "User marked herself safe from mobile app.")
                )
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                Toast.makeText(this@AlertSentActivity, "✅ Alert resolved. Contacts notified you are safe.", Toast.LENGTH_LONG).show()
                returnToMain()
            }
        }
    }

    private fun returnToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun startPeriodicPolling() {
        lifecycleScope.launch {
            while (isActive) {
                delay(6000)
                loadIncidentStatus()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        escalationTimer?.cancel()
    }
}
