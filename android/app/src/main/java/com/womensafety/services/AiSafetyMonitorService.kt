package com.womensafety.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.womensafety.R
import com.womensafety.ui.MainActivity
import com.womensafety.ui.alerts.SosCountdownActivity
import kotlin.math.sqrt

class AiSafetyMonitorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isMonitoringSound = false
    private var audioRecord: AudioRecord? = null
    private var soundMonitoringThread: Thread? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastThreatTriggerTime: Long = 0

    companion object {
        const val CHANNEL_ID = "ai_safety_monitor_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_MONITORING = "com.womensafety.START_AI_MONITORING"
        const val ACTION_STOP_MONITORING = "com.womensafety.STOP_AI_MONITORING"
        const val ACTION_AI_THREAT_DETECTED = "com.womensafety.AI_THREAT_DETECTED"
        const val EXTRA_THREAT_TYPE = "extra_threat_type"

        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        isRunning = true
        // 1. Register Accelerometer sensor for sudden impact / fall detection
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // 2. Start audio level monitor
        startSoundDetection()
    }

    private fun stopMonitoring() {
        isRunning = false
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {}

        isMonitoringSound = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {}
        soundMonitoringThread = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val acceleration = sqrt((x * x + y * y + z * z).toDouble())

        // Sudden high-g impact / fall detection threshold (~32 m/s², ordinary gravity is 9.8)
        if (acceleration > 34.0) {
            onThreatDetected("ai_motion", "Sudden Impact / Fall Detected (${String.format("%.1f", acceleration)} m/s²)")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startSoundDetection() {
        if (isMonitoringSound) return
        isMonitoringSound = true

        soundMonitoringThread = Thread {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1024)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.startRecording()
                    val buffer = ShortArray(bufferSize)

                    while (isMonitoringSound && isRunning) {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readSize > 0) {
                            var maxAmp = 0
                            for (i in 0 until readSize) {
                                val abs = kotlin.math.abs(buffer[i].toInt())
                                if (abs > maxAmp) maxAmp = abs
                            }

                            // Amplitude spike threshold (loud scream / distress noise > 22000)
                            if (maxAmp > 24000) {
                                mainHandler.post {
                                    onThreatDetected("ai_sound", "Loud Distress Scream Detected ($maxAmp amplitude)")
                                }
                                Thread.sleep(2000) // Brief debounce
                            }
                        }
                        Thread.sleep(100)
                    }
                }
            } catch (_: SecurityException) {
                // Audio recording permission not granted
            } catch (_: Exception) {}
        }.apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private fun onThreatDetected(threatType: String, description: String) {
        val now = System.currentTimeMillis()
        if (now - lastThreatTriggerTime < 12000) return // 12-second debounce
        lastThreatTriggerTime = now

        // Broadcast to active UI screen
        val broadcastIntent = Intent(ACTION_AI_THREAT_DETECTED).apply {
            putExtra(EXTRA_THREAT_TYPE, threatType)
            putExtra("extra_description", description)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        // Launch SOS Countdown Activity with cancellation countdown
        val countdownIntent = Intent(this, SosCountdownActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SosCountdownActivity.EXTRA_TRIGGER_TYPE, threatType)
        }
        startActivity(countdownIntent)
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AiSafetyMonitorService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Safety Monitor Active 🧠")
            .setContentText("Continuously analyzing motion and sound for threats.")
            .setSmallIcon(R.drawable.ic_nav_alerts)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(R.drawable.ic_cancel_neon, "Stop Monitoring", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Safety Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while AI sensory monitoring is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
