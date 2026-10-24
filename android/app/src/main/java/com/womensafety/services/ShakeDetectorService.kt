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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.womensafety.R
import com.womensafety.ui.MainActivity
import com.womensafety.ui.alerts.SosCountdownActivity
import kotlin.math.sqrt

class ShakeDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var lastShakeTimestamp: Long = 0
    private var shakeCount: Int = 0

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 500
        private const val SHAKE_RESET_TIME_MS = 3000
        private const val NOTIFICATION_CHANNEL_ID = "shake_sensor_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ShakeDetectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShakeDetectorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        } catch (_: Exception) {}

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()

            if (lastShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }

            if (lastShakeTimestamp + SHAKE_RESET_TIME_MS < now) {
                shakeCount = 0
            }

            lastShakeTimestamp = now
            shakeCount++

            // Require 2 quick shakes to prevent false alarms in handbag / walking
            if (shakeCount >= 2) {
                shakeCount = 0
                triggerShakeEmergency()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerShakeEmergency() {
        val intent = Intent(this, SosCountdownActivity::class.java).apply {
            putExtra(SosCountdownActivity.EXTRA_TRIGGER_TYPE, "shake")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Shake & Sensor Safety Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors device accelerometer for emergency shake gesture"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Safety Shield Active")
            .setContentText("Vigorous shake gesture will activate Emergency SOS")
            .setSmallIcon(R.drawable.ic_safety_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
