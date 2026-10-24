package com.womensafety.ui

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.womensafety.databinding.ActivityFakeIncomingCallBinding

class FakeIncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeIncomingCallBinding
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var callSeconds = 0
    private var isCallConnected = false

    private val timeoutRunnable = Runnable {
        if (!isCallConnected) {
            finishCall()
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isCallConnected) {
                callSeconds++
                val mins = callSeconds / 60
                val secs = callSeconds % 60
                binding.tvCallStatus.text = String.format("%02d:%02d", mins, secs)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeScreenAndShowOverLockscreen()

        binding = ActivityFakeIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerName = intent.getStringExtra("extra_caller_name") ?: "Mom ❤️"
        binding.tvCallerName.text = callerName

        startRingingAndVibration()
        setupCallControls()

        // 30 seconds auto-dismiss if not answered
        handler.postDelayed(timeoutRunnable, 30000)
    }

    private fun wakeScreenAndShowOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun startRingingAndVibration() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (_: Exception) {}

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(pattern, 0)
            }
        }
    }

    private fun stopRingingAndVibration() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun setupCallControls() {
        binding.btnDeclineCall.setOnClickListener {
            finishCall()
        }

        binding.btnAcceptCall.setOnClickListener {
            connectCall()
        }

        binding.btnEndCall.setOnClickListener {
            finishCall()
        }
    }

    private fun connectCall() {
        isCallConnected = true
        stopRingingAndVibration()
        handler.removeCallbacks(timeoutRunnable)

        binding.layoutIncomingControls.visibility = View.GONE
        binding.layoutInCallControls.visibility = View.VISIBLE
        binding.btnEndCall.visibility = View.VISIBLE
        binding.tvCallStatus.text = "00:00"

        handler.postDelayed(timerRunnable, 1000)
    }

    private fun finishCall() {
        stopRingingAndVibration()
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacks(timerRunnable)
        finish()
    }

    override fun onDestroy() {
        stopRingingAndVibration()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
