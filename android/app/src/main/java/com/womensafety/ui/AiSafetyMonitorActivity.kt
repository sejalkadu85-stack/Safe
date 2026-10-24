package com.womensafety.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.womensafety.R
import com.womensafety.databinding.ActivityAiSafetyMonitorBinding
import com.womensafety.services.AiSafetyMonitorService
import com.womensafety.ui.alerts.SosCountdownActivity
import kotlin.math.sqrt

class AiSafetyMonitorActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityAiSafetyMonitorBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO = 301
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiSafetyMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setupToolbar()
        setupMasterToggle()
        setupTestButtons()
        updateStatusBadge(AiSafetyMonitorService.isRunning)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupMasterToggle() {
        binding.switchMasterMonitor.isChecked = AiSafetyMonitorService.isRunning

        binding.switchMasterMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAudioPermissionAndStart()
            } else {
                val stopIntent = Intent(this, AiSafetyMonitorService::class.java).apply {
                    action = AiSafetyMonitorService.ACTION_STOP_MONITORING
                }
                startService(stopIntent)
                updateStatusBadge(false)
                Toast.makeText(this, "AI Safety Monitor Paused", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAudioPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            startAiService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            startAiService()
        }
    }

    private fun startAiService() {
        val startIntent = Intent(this, AiSafetyMonitorService::class.java).apply {
            action = AiSafetyMonitorService.ACTION_START_MONITORING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(startIntent)
        } else {
            startService(startIntent)
        }
        updateStatusBadge(true)
        Toast.makeText(this, "AI Guardian Activated! Protecting you in background 🧠", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusBadge(active: Boolean) {
        if (active) {
            binding.tvLiveStatusBadge.text = "ACTIVE 🟢"
            binding.tvLiveStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
        } else {
            binding.tvLiveStatusBadge.text = "PAUSED ⚪"
            binding.tvLiveStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun setupTestButtons() {
        binding.btnTestFall.setOnClickListener {
            showTriggerConfirmation("ai_motion", "Simulate Sudden Impact / Fall Detection")
        }

        binding.btnTestScream.setOnClickListener {
            showTriggerConfirmation("ai_sound", "Simulate Loud Distress Scream Detection")
        }
    }

    private fun showTriggerConfirmation(triggerType: String, title: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("This will launch the emergency SOS countdown. You will have 5 seconds to cancel before emergency signals are broadcast.")
            .setPositiveButton("Simulate Alert") { _, _ ->
                val intent = Intent(this, SosCountdownActivity::class.java).apply {
                    putExtra(SosCountdownActivity.EXTRA_TRIGGER_TYPE, triggerType)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        binding.switchMasterMonitor.isChecked = AiSafetyMonitorService.isRunning
        updateStatusBadge(AiSafetyMonitorService.isRunning)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt((x * x + y * y + z * z).toDouble())

        binding.tvMotionValue.text = String.format("%.1f m/s²", g)
        binding.progressMotion.progress = g.toInt().coerceIn(0, 50)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
