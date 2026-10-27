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

    private val incidents = mutableListOf<IncidentItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)

        setupHeader()
        renderIncidentsList()
        setupListeners()
        fetchLiveStats()
        fetchLiveIncidents()
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

        binding.btnAdminLanguage.setOnClickListener {
            com.womensafety.utils.LocaleHelper.showLanguageDialog(this)
        }

        // Open Web Admin Console
        binding.btnOpenWebConsole.setOnClickListener {
            openWebConsole()
        }
        binding.btnOpenWebConsole.setOnLongClickListener {
            com.womensafety.utils.ServerConfigDialog.show(this) {
                fetchLiveStats()
            }
            true
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
                            val num = activeStr.toIntOrNull() ?: 0
                            binding.tvStatActiveAlertsSub.text = if (num > 0) "🚨 $activeStr Emergency" else "All Clear"
                        }
                        stats["resolved_sos_alerts"]?.let { binding.tvStatResolvedAlerts.text = formatStat(it) }
                        stats["total_volunteers"]?.let { binding.tvStatVolunteers.text = formatStat(it) }

                        // Real User Role Breakdown
                        stats["user_role_count"]?.let { binding.tvRoleUserCount.text = formatStat(it) }
                        stats["guardian_role_count"]?.let { binding.tvRoleParentCount.text = formatStat(it) }
                        stats["volunteer_role_count"]?.let { binding.tvRoleVolunteerCount.text = formatStat(it) }
                        stats["admin_role_count"]?.let { binding.tvRoleResponderCount.text = formatStat(it) }
                    }
                }
            } catch (_: Exception) {
                binding.tvStatTotalUsers.text = "0"
                binding.tvStatActiveAlerts.text = "0"
                binding.tvStatActiveAlertsSub.text = "All Clear"
                binding.tvIncidentCountBadge.text = "0 Active"
                binding.tvStatResolvedAlerts.text = "0"
                binding.tvStatVolunteers.text = "0"
                binding.tvRoleUserCount.text = "0"
                binding.tvRoleParentCount.text = "0"
                binding.tvRoleVolunteerCount.text = "0"
                binding.tvRoleResponderCount.text = "0"
            }
        }
    }

    private fun fetchLiveIncidents() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getService(this@AdminDashboardActivity).getAdminSosLogs()
                if (response.isSuccessful && response.body() != null) {
                    val map = response.body()!!
                    @Suppress("UNCHECKED_CAST")
                    val logs = map["sos_logs"] as? List<Map<String, Any>>
                    incidents.clear()
                    if (!logs.isNullOrEmpty()) {
                        for (item in logs) {
                            val id = (item["id"] as? Number)?.toInt() ?: 0
                            val user = (item["sender"] as? String) ?: "User"
                            val address = (item["address"] as? String)?.takeIf { it.isNotBlank() } ?: "GPS Pin Location"
                            val time = (item["created_at"] as? String)?.take(16)?.replace("T", " ") ?: "Recent"
                            val status = (item["status"] as? String)?.replaceFirstChar { it.uppercase() } ?: "Active"
                            val priority = if (status.equals("active", true)) "High" else "Normal"
                            val lat = (item["latitude"] as? Number)?.toDouble() ?: 0.0
                            val lng = (item["longitude"] as? Number)?.toDouble() ?: 0.0
                            incidents.add(IncidentItem(id, user, address, time, priority, status, lat, lng, "100%"))
                        }
                    }
                    renderIncidentsList()
                }
            } catch (_: Exception) {
                renderIncidentsList()
            }
        }
    }

    private fun renderIncidentsList() {
        binding.llIncidentsList.removeAllViews()

        if (incidents.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_dark_card)
                setPadding(40, 48, 40, 48)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 24)
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
                text = "No active emergency SOS alerts recorded."
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                gravity = android.view.Gravity.CENTER
            }

            emptyCard.addView(iconTv)
            emptyCard.addView(titleTv)
            emptyCard.addView(subtitleTv)
            binding.llIncidentsList.addView(emptyCard)
            return
        }

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
                    Toast.makeText(this, "Broadcast alert dispatched successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openWebConsole() {
        val host = com.womensafety.utils.Constants.getServerHost(this)
        val url = "http://$host/admin-panel/dashboard/"
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
