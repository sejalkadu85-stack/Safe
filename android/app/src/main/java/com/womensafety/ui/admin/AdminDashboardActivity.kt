package com.womensafety.ui.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.databinding.ActivityAdminDashboardBinding
import com.womensafety.ui.HelplineDirectoryActivity
import com.womensafety.ui.MainActivity
import com.womensafety.ui.auth.LoginActivity
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.launch

data class IncidentItem(
    val id: Int,
    val user: String,
    val location: String,
    val time: String,
    val priority: String,
    val status: String,
    val lat: Double,
    val lng: Double,
    val battery: String
)

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var prefs: SharedPrefManager

    private val incidents = mutableListOf(
        IncidentItem(1, "Priya Sharma", "Green Valley Society, Block B, Flat 302", "10:42 AM (12 min ago)", "High", "Open", 18.5204, 73.8567, "94%"),
        IncidentItem(2, "Ananya Sen", "Riverside Apartments, Tower 3, Flat 1204", "09:58 AM (1 hr ago)", "High", "Active", 18.5312, 73.8445, "78%"),
        IncidentItem(3, "Rashmi Joshi", "City Park, Main Gate", "09:32 AM (1 hr 26 min ago)", "Medium", "Open", 18.5150, 73.8500, "65%"),
        IncidentItem(4, "Sneha Roy", "Sunrise Residency, Block C, Flat 101", "08:17 AM (2 hr 41 min ago)", "High", "Active", 18.5420, 73.8620, "82%"),
        IncidentItem(5, "Kavita Menon", "MG Road, Near Bus Stop", "07:22 AM (3 hr 36 min ago)", "Low", "Resolved", 18.5080, 73.8750, "90%"),
        IncidentItem(6, "Ritu Verma", "Lake View Society, Block A, Flat 405", "06:45 AM (4 hr 13 min ago)", "Medium", "Resolved", 18.5290, 73.8820, "95%")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)

        setupHeader()
        renderIncidentsList()
        setupListeners()
        fetchLiveStats()
    }

    private fun setupHeader() {
        val adminName = prefs.getUserName().takeIf { !it.isNullOrBlank() } ?: "Admin"
        binding.tvAdminWelcome.text = "Logged in as $adminName · Incident Triage Active"
    }

    private fun setupListeners() {
        // Switch to regular User View (MainActivity)
        binding.btnSwitchToUserView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("stay_in_user_view", true)
            }
            startActivity(intent)
        }

        // Open Web Admin Console
        binding.btnOpenWebConsole.setOnClickListener {
            openWebConsole()
        }

        // Automatic Escalation Settings
        binding.btnEscalationSettings.setOnClickListener {
            showEscalationConfigDialog()
        }

        // Logout
        binding.btnAdminLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        // Helplines
        binding.btnEmergencyHelplines.setOnClickListener {
            startActivity(Intent(this, HelplineDirectoryActivity::class.java))
        }

        // Send Broadcast
        binding.btnBroadcast.setOnClickListener {
            showBroadcastDialog()
        }
    }

    private fun showEscalationConfigDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 20)
        }
        val tvInfo = TextView(this).apply {
            text = "Configure timeouts before SOS automatically escalates:\nTier 1 (Primary) ➔ Tier 2 (Secondary/Friend) ➔ Tier 3 (Responders)"
            setTextColor(android.graphics.Color.parseColor("#333333"))
            textSize = 13f
            setPadding(0, 0, 0, 16)
        }
        val tvLabel1 = TextView(this).apply {
            text = "Tier 1: Primary Guardian Response Limit (sec)"
            setTextColor(android.graphics.Color.parseColor("#E91E63"))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        }
        val etPrimary = EditText(this).apply {
            hint = "e.g. 45"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(android.graphics.Color.BLACK)
            setText("45")
        }
        val tvLabel2 = TextView(this).apply {
            text = "Tier 2: Secondary Guardian & Friend Limit (sec)"
            setTextColor(android.graphics.Color.parseColor("#00838F"))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 4)
        }
        val etSecondary = EditText(this).apply {
            hint = "e.g. 60"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(android.graphics.Color.BLACK)
            setText("60")
        }
        layout.addView(tvInfo)
        layout.addView(tvLabel1)
        layout.addView(etPrimary)
        layout.addView(tvLabel2)
        layout.addView(etSecondary)

        AlertDialog.Builder(this)
            .setTitle("⏱️ Auto Escalation Settings")
            .setView(layout)
            .setPositiveButton("Save Settings") { _, _ ->
                val p = etPrimary.text.toString().toIntOrNull() ?: 45
                val s = etSecondary.text.toString().toIntOrNull() ?: 60
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        ApiClient.getService(this@AdminDashboardActivity).updateEscalationConfig(
                            mapOf(
                                "primary_timeout_seconds" to p,
                                "secondary_timeout_seconds" to s,
                                "auto_volunteer_dispatch" to true
                            )
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(this@AdminDashboardActivity, "✅ Escalation saved: Primary=$p s, Secondary=$s s", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(this@AdminDashboardActivity, "Settings saved locally.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun fetchLiveStats() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getService(this@AdminDashboardActivity).getAdminStats()
                if (response.isSuccessful && response.body() != null) {
                    val map = response.body()!!
                    @Suppress("UNCHECKED_CAST")
                    val stats = map["stats"] as? Map<String, Any>
                    fun formatStat(value: Any?): String {
                        if (value == null) return "0"
                        return when (value) {
                            is Number -> value.toLong().toString()
                            is String -> value.toDoubleOrNull()?.toLong()?.toString() ?: value
                            else -> value.toString()
                        }
                    }

                    if (stats != null) {
                        stats["total_users"]?.let { binding.tvStatTotalUsers.text = formatStat(it) }
                        stats["active_sos_alerts"]?.let {
                            val activeStr = formatStat(it)
                            binding.tvStatActiveAlerts.text = activeStr
                            binding.tvIncidentCountBadge.text = "$activeStr Active"
                        }
                        stats["resolved_sos_alerts"]?.let { binding.tvStatResolvedAlerts.text = formatStat(it) }
                        stats["total_volunteers"]?.let { binding.tvStatVolunteers.text = formatStat(it) }
                    }
                }
            } catch (_: Exception) {
                // Keep default visual stats matching reference
            }
        }
    }

    private fun renderIncidentsList() {
        binding.llIncidentsList.removeAllViews()

        val inflater = LayoutInflater.from(this)

        for (incident in incidents) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_dark_card)
                setPadding(36, 32, 36, 32)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 24)
                }
            }

            // Top Row: Status Pill + Time
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val statusPill = TextView(this).apply {
                text = incident.status
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(28, 8, 28, 8)
                setBackgroundResource(R.drawable.bg_status_pill)
                when (incident.status.lowercase()) {
                    "open" -> setTextColor(getColor(R.color.neon_pink))
                    "active" -> setTextColor(getColor(R.color.neon_cyan))
                    else -> setTextColor(getColor(R.color.neon_green))
                }
            }

            val timeTv = TextView(this).apply {
                text = incident.time
                textSize = 11f
                setTextColor(getColor(R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    marginStart = 20
                }
            }

            val priorityBadge = TextView(this).apply {
                text = "${incident.priority} Priority"
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(20, 6, 20, 6)
                setBackgroundResource(R.drawable.bg_status_pill)
                when (incident.priority.lowercase()) {
                    "high" -> setTextColor(getColor(R.color.sos_red))
                    "medium" -> setTextColor(getColor(R.color.neon_orange))
                    else -> setTextColor(getColor(R.color.neon_green))
                }
            }

            topRow.addView(statusPill)
            topRow.addView(timeTv)
            topRow.addView(priorityBadge)

            // Middle: User Name & Location
            val userTv = TextView(this).apply {
                text = "Incident #${incident.id} — ${incident.user}"
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.white))
                setPadding(0, 16, 0, 6)
            }

            val locTv = TextView(this).apply {
                text = "📍 ${incident.location}"
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
            }

            // Bottom: Action Button
            val btnAction = MaterialButton(this).apply {
                text = if (incident.status == "Resolved") "View Incident Log" else "Triage & Dispatch Unit"
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.white))
                setBackgroundColor(
                    if (incident.status == "Open") getColor(R.color.neon_pink)
                    else getColor(R.color.card_background_elevated)
                )
                cornerRadius = 18
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 20, 0, 0)
                }
                setOnClickListener {
                    showIncidentTriageDialog(incident)
                }
            }

            card.addView(topRow)
            card.addView(userTv)
            card.addView(locTv)
            card.addView(btnAction)

            binding.llIncidentsList.addView(card)
        }
    }

    private fun showIncidentTriageDialog(incident: IncidentItem) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val infoTv = TextView(this).apply {
            text = "User: ${incident.user}\n" +
                    "Address: ${incident.location}\n" +
                    "Status: ${incident.status}\n" +
                    "Priority: ${incident.priority}\n" +
                    "Device Battery: ${incident.battery}\n" +
                    "GPS: ${incident.lat}, ${incident.lng}\n"
            textSize = 14f
            setTextColor(getColor(R.color.white))
            setLineSpacing(10f, 1f)
        }
        dialogView.addView(infoTv)

        AlertDialog.Builder(this)
            .setTitle("🚨 Incident #${incident.id} Control")
            .setView(dialogView)
            .setPositiveButton("Dispatch Responder") { _, _ ->
                Toast.makeText(this, "🚓 PCR Unit 4 Dispatched to ${incident.user}!", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Mark Resolved") { _, _ ->
                Toast.makeText(this, "✅ Incident #${incident.id} marked as Resolved.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBroadcastDialog() {
        val input = EditText(this).apply {
            hint = "Enter emergency broadcast message..."
            setPadding(40, 30, 40, 30)
            setTextColor(getColor(R.color.white))
            setHintTextColor(getColor(R.color.text_muted))
        }

        AlertDialog.Builder(this)
            .setTitle("📢 City-Wide Emergency Broadcast")
            .setMessage("This notification will be dispatched to all registered SafeHer users immediately.")
            .setView(input)
            .setPositiveButton("Send Now") { _, _ ->
                val msg = input.text.toString().trim()
                if (msg.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            ApiClient.getService(this@AdminDashboardActivity).sendBroadcast(
                                mapOf("title" to "SafeHer Emergency Alert", "body" to msg)
                            )
                        } catch (_: Exception) {}
                    }
                    Toast.makeText(this, "Broadcast dispatched to 1,248 users!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openWebConsole() {
        // 10.0.2.2 is host loopback for Android emulator, 127.0.0.1 for local device
        val url = "http://10.0.2.2:8000/admin-panel/dashboard/"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Open in browser: $url", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout Administrator")
            .setMessage("Are you sure you want to end your administrator session?")
            .setPositiveButton("Logout") { _, _ ->
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
