package com.womensafety.ui.volunteer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.databinding.ActivityActiveAssistanceBinding
import kotlinx.coroutines.launch

class ActiveAssistanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActiveAssistanceBinding

    private var requestId: Int = 1
    private var victimName: String = "Priya Sharma"
    private var victimPhone: String = "+919021380010"
    private var victimAddress: String = "Green Valley Society, Block B, Flat 302"
    private var victimLat: Double = 18.5204
    private var victimLng: Double = 73.8567
    private var currentStep: Int = 1 // 1: Accepted, 2: On The Way, 3: Arrived, 4: Resolved

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActiveAssistanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read intent extras if passed
        requestId = intent.getIntExtra("request_id", 1)
        victimName = intent.getStringExtra("victim_name") ?: "Priya Sharma"
        victimPhone = intent.getStringExtra("victim_phone") ?: "+919021380010"
        victimAddress = intent.getStringExtra("victim_address") ?: "Green Valley Society, Block B, Flat 302"
        victimLat = intent.getDoubleExtra("victim_lat", 18.5204)
        victimLng = intent.getDoubleExtra("victim_lng", 73.8567)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.tvMissionVictimName.text = victimName
        binding.tvMissionAddress.text = "📍 $victimAddress"
        updateStepperVisuals()
    }

    private fun updateStepperVisuals() {
        when (currentStep) {
            1 -> {
                binding.stepDot1.setBackgroundColor(getColor(R.color.neon_cyan))
                binding.stepDot2.setBackgroundColor(getColor(R.color.card_background_elevated))
                binding.stepDot3.setBackgroundColor(getColor(R.color.card_background_elevated))
                binding.stepDot4.setBackgroundColor(getColor(R.color.card_background_elevated))
                binding.btnProgressStatus.text = "🚗 I am On the Way"
                binding.btnProgressStatus.setBackgroundColor(getColor(R.color.neon_pink))
            }
            2 -> {
                binding.stepDot1.setBackgroundColor(getColor(R.color.neon_green))
                binding.stepDot2.setBackgroundColor(getColor(R.color.neon_cyan))
                binding.stepDot3.setBackgroundColor(getColor(R.color.card_background_elevated))
                binding.stepDot4.setBackgroundColor(getColor(R.color.card_background_elevated))
                binding.btnProgressStatus.text = "📍 I Have Arrived at Location"
                binding.btnProgressStatus.setBackgroundColor(getColor(R.color.neon_cyan_dark))
            }
            3 -> {
                binding.stepDot1.setBackgroundColor(getColor(R.color.neon_green))
                binding.stepDot2.setBackgroundColor(getColor(R.color.neon_green))
                binding.stepDot3.setBackgroundColor(getColor(R.color.neon_cyan))
                binding.stepDot4.setBackgroundColor(getColor(R.color.card_background_elevated))
                binding.btnProgressStatus.text = "✅ Resolve Incident & Mark Safe"
                binding.btnProgressStatus.setBackgroundColor(getColor(R.color.neon_green))
            }
            4 -> {
                binding.stepDot1.setBackgroundColor(getColor(R.color.neon_green))
                binding.stepDot2.setBackgroundColor(getColor(R.color.neon_green))
                binding.stepDot3.setBackgroundColor(getColor(R.color.neon_green))
                binding.stepDot4.setBackgroundColor(getColor(R.color.neon_green))
                binding.btnProgressStatus.text = "Mission Completed 🎉"
                binding.btnProgressStatus.isEnabled = false
            }
        }
    }

    private fun setupListeners() {
        binding.btnBackAssistance.setOnClickListener {
            finish()
        }

        // Call 112
        binding.btnEmergencyCall112.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }

        // Call User
        binding.btnCallVictim.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$victimPhone")))
        }

        // Navigation in Google Maps
        binding.btnNavigateMap.setOnClickListener {
            val uri = Uri.parse("google.navigation:q=$victimLat,$victimLng")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            try {
                startActivity(mapIntent)
            } catch (_: Exception) {
                // Fallback to browser or generic map viewer
                val genericUri = Uri.parse("https://maps.google.com/?q=$victimLat,$victimLng")
                startActivity(Intent(Intent.ACTION_VIEW, genericUri))
            }
        }

        // Progress Status Flow
        binding.btnProgressStatus.setOnClickListener {
            advanceMissionStep()
        }

        // Cancel / Withdraw
        binding.btnCancelAssistance.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Withdraw from Mission?")
                .setMessage("Are you sure you cannot assist? The alert will be reassigned to other nearby volunteers.")
                .setPositiveButton("Withdraw") { _, _ ->
                    notifyBackendAction("decline")
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun advanceMissionStep() {
        when (currentStep) {
            1 -> {
                notifyBackendAction("on_the_way")
                currentStep = 2
                updateStepperVisuals()
                Toast.makeText(this, "Status: On the Way to $victimName", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                notifyBackendAction("arrived")
                currentStep = 3
                updateStepperVisuals()
                Toast.makeText(this, "Status: You have Arrived! Ensure user safety.", Toast.LENGTH_SHORT).show()
            }
            3 -> {
                AlertDialog.Builder(this)
                    .setTitle("Mark Assistance Completed")
                    .setMessage("Has the user been verified safe and out of distress?")
                    .setPositiveButton("Yes, Safe ✅") { _, _ ->
                        notifyBackendAction("complete")
                        currentStep = 4
                        updateStepperVisuals()
                        Toast.makeText(this, "Assistance Mission Resolved! Thank you for protecting your community.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .setNegativeButton("Not Yet", null)
                    .show()
            }
        }
    }

    private fun notifyBackendAction(action: String) {
        lifecycleScope.launch {
            try {
                // Call community API if reachable
                ApiClient.getService(this@ActiveAssistanceActivity).respondVolunteerRequest(
                    id = requestId,
                    body = mapOf("action" to action)
                )
            } catch (_: Exception) {
                // Offline fallback - status progresses locally seamlessly
            }
        }
    }
}
