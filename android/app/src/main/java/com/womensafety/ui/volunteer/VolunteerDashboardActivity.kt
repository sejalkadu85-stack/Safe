package com.womensafety.ui.volunteer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.databinding.ActivityVolunteerDashboardBinding
import com.womensafety.ui.MainActivity
import com.womensafety.ui.auth.LoginActivity
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.launch

data class NearbySosItem(
    val id: Int,
    val victimName: String,
    val phone: String,
    val emergencyType: String,
    val location: String,
    val distance: String,
    val eta: String,
    val priority: String,
    val lat: Double,
    val lng: Double,
    val battery: String
)

class VolunteerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVolunteerDashboardBinding
    private lateinit var prefs: SharedPrefManager

    private var isOnline = true
    private var activeMission: NearbySosItem? = null

    private val nearbyRequests = mutableListOf<NearbySosItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVolunteerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)

        setupHeader()
        setupBottomNav()
        setupDutyToggle()
        renderNearbyRequests()
        renderIncidentsTab()
        setupChatTab()
        setupListeners()
        fetchVolunteerProfile()
    }

    override fun onResume() {
        super.onResume()
        updateActiveMissionCard()
    }

    private fun setupHeader() {
        val name = prefs.getUserName().takeIf { !it.isNullOrBlank() } ?: "Hero Volunteer"
        binding.tvVolGreeting.text = name.replaceFirstChar { it.uppercase() }
        binding.tvProfileName.text = name.replaceFirstChar { it.uppercase() }
    }

    private fun setupDutyToggle() {
        binding.btnDutyToggle.setOnClickListener {
            isOnline = !isOnline
            if (isOnline) {
                binding.tvDutyStatus.text = "ONLINE"
                binding.tvDutyStatus.setTextColor(getColor(R.color.neon_green))
                binding.viewDutyDot.setBackgroundResource(R.drawable.bg_dot_active)
                binding.cardRadarStatus.visibility = View.VISIBLE
                Toast.makeText(this, "You are now ONLINE and available for nearby SOS dispatches.", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvDutyStatus.text = "OFFLINE"
                binding.tvDutyStatus.setTextColor(getColor(R.color.text_muted))
                binding.viewDutyDot.setBackgroundResource(R.drawable.bg_dot_inactive)
                binding.cardRadarStatus.visibility = View.GONE
                Toast.makeText(this, "You are OFFLINE. New SOS alerts will be routed to other responders.", Toast.LENGTH_SHORT).show()
            }
            syncOnlineStatusWithBackend(isOnline)
        }
    }

    private fun syncOnlineStatusWithBackend(online: Boolean) {
        lifecycleScope.launch {
            try {
                ApiClient.getService(this@VolunteerDashboardActivity).updateVolunteerProfile(
                    mapOf("is_available" to online)
                )
            } catch (_: Exception) {}
        }
    }

    private fun setupBottomNav() {
        binding.volBottomNav.setOnItemSelectedListener { item ->
            // Hide all tab views
            binding.tabHomeView.visibility = View.GONE
            binding.tabIncidentsView.visibility = View.GONE
            binding.tabMapView.visibility = View.GONE
            binding.tabChatView.visibility = View.GONE
            binding.tabProfileView.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_vol_home -> {
                    binding.tabHomeView.visibility = View.VISIBLE
                    true
                }
                R.id.nav_vol_incidents -> {
                    binding.tabIncidentsView.visibility = View.VISIBLE
                    true
                }
                R.id.nav_vol_map -> {
                    binding.tabMapView.visibility = View.VISIBLE
                    true
                }
                R.id.nav_vol_chat -> {
                    binding.tabChatView.visibility = View.VISIBLE
                    true
                }
                R.id.nav_vol_profile -> {
                    binding.tabProfileView.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun renderNearbyRequests() {
        binding.llNearbyRequestsList.removeAllViews()

        if (nearbyRequests.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_dark_card)
                setPadding(40, 48, 40, 48)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 20)
                }
            }

            val iconTv = TextView(this).apply {
                text = "🛡️"
                textSize = 32f
                gravity = android.view.Gravity.CENTER
            }

            val titleTv = TextView(this).apply {
                text = "All Zones Clear"
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.white))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 12, 0, 4)
            }

            val subtitleTv = TextView(this).apply {
                text = "No active volunteer assistance requests in your area."
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                gravity = android.view.Gravity.CENTER
            }

            emptyCard.addView(iconTv)
            emptyCard.addView(titleTv)
            emptyCard.addView(subtitleTv)
            binding.llNearbyRequestsList.addView(emptyCard)
            return
        }

        for (sos in nearbyRequests) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_dark_card)
                setPadding(36, 32, 36, 32)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 20)
                }
            }

            // Top Header: Priority Badge + ETA
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val priorityBadge = TextView(this).apply {
                text = "${sos.priority} Priority"
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(22, 6, 22, 6)
                setBackgroundResource(R.drawable.bg_status_pill)
                when (sos.priority.lowercase()) {
                    "high" -> setTextColor(getColor(R.color.sos_red))
                    else -> setTextColor(getColor(R.color.neon_orange))
                }
            }

            val distEtaTv = TextView(this).apply {
                text = "${sos.distance} · ${sos.eta}"
                textSize = 11f
                setTextColor(getColor(R.color.neon_cyan))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    marginStart = 16
                }
            }

            topRow.addView(priorityBadge)
            topRow.addView(distEtaTv)

            // Victim Name & Emergency Type
            val nameTv = TextView(this).apply {
                text = sos.victimName
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.white))
                setPadding(0, 14, 0, 4)
            }

            val descTv = TextView(this).apply {
                text = "⚠️ ${sos.emergencyType}"
                textSize = 13f
                setTextColor(getColor(R.color.neon_pink))
            }

            val locTv = TextView(this).apply {
                text = "📍 ${sos.location}"
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 4, 0, 16)
            }

            // Buttons: Accept SOS & Decline
            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val btnAccept = MaterialButton(this).apply {
                text = "🚨 Accept SOS"
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.white))
                setBackgroundColor(getColor(R.color.neon_pink))
                cornerRadius = 14
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                    marginEnd = 12
                }
                setOnClickListener {
                    acceptSosIncident(sos)
                }
            }

            val btnInspect = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Inspect"
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
                setOnClickListener {
                    showIncidentDetailsDialog(sos)
                }
            }

            btnRow.addView(btnAccept)
            btnRow.addView(btnInspect)

            card.addView(topRow)
            card.addView(nameTv)
            card.addView(descTv)
            card.addView(locTv)
            card.addView(btnRow)

            binding.llNearbyRequestsList.addView(card)
        }
    }

    private fun renderIncidentsTab() {
        binding.llAllIncidentsList.removeAllViews()
        // Duplicate full list in Incidents tab for filtering
        for (sos in nearbyRequests) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_dark_card)
                setPadding(32, 24, 32, 24)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }

            val t = TextView(this).apply {
                text = "${sos.victimName} (${sos.distance})"
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.white))
            }
            val d = TextView(this).apply {
                text = "📍 ${sos.location} · ${sos.emergencyType}"
                textSize = 11f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 4, 0, 8)
            }
            val btn = MaterialButton(this).apply {
                text = "View Incident & Triage"
                textSize = 11f
                setTextColor(getColor(R.color.white))
                setBackgroundColor(getColor(R.color.card_background_elevated))
                cornerRadius = 12
                setOnClickListener {
                    acceptSosIncident(sos)
                }
            }
            item.addView(t)
            item.addView(d)
            item.addView(btn)
            binding.llAllIncidentsList.addView(item)
        }
    }

    private fun acceptSosIncident(sos: NearbySosItem) {
        activeMission = sos
        updateActiveMissionCard()

        val intent = Intent(this, ActiveAssistanceActivity::class.java).apply {
            putExtra("request_id", sos.id)
            putExtra("victim_name", sos.victimName)
            putExtra("victim_phone", sos.phone)
            putExtra("victim_address", sos.location)
            putExtra("victim_lat", sos.lat)
            putExtra("victim_lng", sos.lng)
        }
        startActivity(intent)
    }

    private fun updateActiveMissionCard() {
        if (activeMission != null) {
            binding.cardActiveMission.visibility = View.VISIBLE
            binding.tvActiveMissionVictim.text = "Assisting: ${activeMission!!.victimName} (${activeMission!!.distance})"
            binding.tvActiveMissionLocation.text = "📍 ${activeMission!!.location}"
        } else {
            binding.cardActiveMission.visibility = View.GONE
        }
    }

    private fun showIncidentDetailsDialog(sos: NearbySosItem) {
        AlertDialog.Builder(this)
            .setTitle("Distress Incident #${sos.id}")
            .setMessage(
                "Victim: ${sos.victimName}\n" +
                "Phone: ${sos.phone}\n" +
                "Type: ${sos.emergencyType}\n" +
                "Location: ${sos.location}\n" +
                "Distance: ${sos.distance} (${sos.eta})\n" +
                "Battery: ${sos.battery}\n" +
                "GPS: ${sos.lat}, ${sos.lng}"
            )
            .setPositiveButton("Accept SOS") { _, _ ->
                acceptSosIncident(sos)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun setupChatTab() {
        binding.chipQuickMsg1.setOnClickListener {
            binding.etChatMessage.setText("🚗 I'm 2 mins away! Stay in a safe place.")
        }
        binding.chipQuickMsg2.setOnClickListener {
            binding.etChatMessage.setText("📍 Stay where you are. I'm right around the corner.")
        }
        binding.chipQuickMsg3.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }

        binding.btnSendChat.setOnClickListener {
            val msg = binding.etChatMessage.text.toString().trim()
            if (msg.isNotEmpty()) {
                val newMsg = TextView(this).apply {
                    text = "You: $msg"
                    textSize = 13f
                    setTextColor(getColor(R.color.white))
                    setBackgroundResource(R.drawable.bg_status_pill)
                    setPadding(28, 16, 28, 16)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.END
                        setMargins(0, 0, 0, 16)
                    }
                }
                binding.llChatMessages.addView(newMsg)
                binding.etChatMessage.setText("")
                Toast.makeText(this, "Message sent to user", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        // Resume active mission from Home tab
        binding.btnResumeActiveMission.setOnClickListener {
            activeMission?.let { acceptSosIncident(it) }
        }

        // Open Map navigation
        binding.btnOpenNavigationMap.setOnClickListener {
            val uri = Uri.parse("geo:18.5204,73.8567?q=18.5204,73.8567(SafeHer+Emergency+Pin)")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        // Switch to personal user mode
        binding.btnProfileSwitchUserView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("stay_in_user_view", true)
            }
            startActivity(intent)
        }

        // Logout
        binding.btnVolLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Log out from your volunteer responder account?")
                .setPositiveButton("Log Out") { _, _ ->
                    prefs.clear()
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun fetchVolunteerProfile() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getService(this@VolunteerDashboardActivity).getVolunteerProfile()
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    val vol = profile.volunteer
                    if (vol != null) {
                        binding.tvStatHelps.text = vol.totalHelps.toString()
                        binding.tvStatRating.text = "${vol.rating} ★"
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
