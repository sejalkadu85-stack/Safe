package com.womensafety.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.womensafety.databinding.ActivityFakeCallBinding

class FakeCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeCallBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnTriggerFakeCall.setOnClickListener {
            scheduleFakeCall()
        }
    }

    private fun scheduleFakeCall() {
        val callerName = when {
            binding.rbPolice.isChecked -> "Inspector Sharma (Police)"
            binding.rbDad.isChecked -> "Dad 🏡"
            binding.rbBoss.isChecked -> "Manager / Office"
            else -> "Mom ❤️"
        }

        val delaySeconds = when {
            binding.rb15s.isChecked -> 15
            binding.rb2m.isChecked -> 120
            binding.rb5m.isChecked -> 300
            else -> 0
        }

        if (delaySeconds == 0) {
            launchCallImmediately(callerName)
        } else {
            val intent = Intent(this, FakeIncomingCallActivity::class.java).apply {
                putExtra("extra_caller_name", callerName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            handler.postDelayed({
                startActivity(intent)
            }, delaySeconds * 1000L)

            val timeLabel = if (delaySeconds < 60) "${delaySeconds}s" else "${delaySeconds / 60} min"
            Toast.makeText(this, "Fake call scheduled in $timeLabel. Keep phone nearby 📞", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun launchCallImmediately(callerName: String) {
        val intent = Intent(this, FakeIncomingCallActivity::class.java).apply {
            putExtra("extra_caller_name", callerName)
        }
        startActivity(intent)
        finish()
    }
}
