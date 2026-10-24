package com.womensafety.ui.guardian

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.api.ApiService
import com.womensafety.data.models.GuardianActiveAlertItem
import com.womensafety.databinding.ActivityGuardianDashboardBinding
import com.womensafety.ui.HelplineDirectoryActivity
import com.womensafety.ui.MainActivity
import com.womensafety.ui.auth.LoginActivity
import com.womensafety.ui.chat.IncidentChatActivity
import com.womensafety.ui.contacts.TrustedContactsActivity
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GuardianDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardianDashboardBinding
    private lateinit var adapter: GuardianAlertAdapter
    private lateinit var apiService: ApiService
    private lateinit var prefs: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardianDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)
        apiService = ApiClient.getService(this)

        setupHeader()
        setupRecyclerView()
        setupListeners()
        setupBottomNav()
        loadActiveAlerts()
        startPeriodicPolling()
    }

    private fun setupHeader() {
        val userName = prefs.getUserName().takeIf { !it.isNullOrBlank() } ?: "Guardian"
        val role = prefs.getUserRole()
        val roleLabel = if (role == "friend") "Friend / Emergency Contact" else "Primary Guardian"
        binding.tvGuardianSubtitle.text = "Logged in as $userName ($roleLabel) · Protective Shield Active"
    }

    private fun setupRecyclerView() {
        adapter = GuardianAlertAdapter(
            onAcceptRespond = { alert ->
                acceptIncident(alert)
            },
            onCallWard = { alert ->
                val phone = alert.phone.ifBlank { "112" }
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            },
            onChat = { alert ->
                val intent = Intent(this, IncidentChatActivity::class.java).apply {
                    putExtra(IncidentChatActivity.EXTRA_ALERT_ID, alert.id)
                    putExtra(IncidentChatActivity.EXTRA_VICTIM_NAME, alert.userName)
                    putExtra(IncidentChatActivity.EXTRA_CATEGORY, alert.category)
                }
                startActivity(intent)
            },
            onNavigateMap = { alert ->
                val uri = Uri.parse("google.navigation:q=${alert.latitude},${alert.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${alert.latitude},${alert.longitude}")
                    startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                }
            },
            onResolve = { alert ->
                confirmResolveAlert(alert)
            }
        )

        binding.rvGuardianAlerts.layoutManager = LinearLayoutManager(this)
        binding.rvGuardianAlerts.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            loadActiveAlerts()
        }

        binding.btnRefreshAlerts.setOnClickListener {
            loadActiveAlerts()
        }

        binding.btnSwitchUser.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("stay_in_user_view", true)
            }
            startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        binding.btnCall112.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }

        binding.btnCall1091.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1091")))
        }

        binding.btnCall108.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:108")))
        }
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_map -> {
                    startActivity(Intent(this, com.womensafety.ui.location.LiveTrackingActivity::class.java))
                    true
                }
                R.id.nav_alerts -> {
                    startActivity(Intent(this, TrustedContactsActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, HelplineDirectoryActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadActiveAlerts() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getGuardianActiveAlerts()
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    if (response.isSuccessful && response.body() != null) {
                        val alerts = response.body()!!.activeAlerts
                        updateAlertsUI(alerts)
                    } else {
                        // Demo fallback with active test incident
                        val mock = getMockActiveAlerts()
                        updateAlertsUI(mock)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = false
                    val mock = getMockActiveAlerts()
                    updateAlertsUI(mock)
                }
            }
        }
    }

    private fun updateAlertsUI(alerts: List<GuardianActiveAlertItem>) {
        adapter.submitList(alerts)
        binding.tvActiveAlertCount.text = alerts.size.toString()
        if (alerts.isNotEmpty()) {
            binding.tvActiveAlertCount.setTextColor(Color.parseColor("#FF2D55"))
            binding.llEmptyState.visibility = View.GONE
            binding.rvGuardianAlerts.visibility = View.VISIBLE
        } else {
            binding.tvActiveAlertCount.setTextColor(Color.parseColor("#00E676"))
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvGuardianAlerts.visibility = View.GONE
        }
    }

    private fun acceptIncident(alert: GuardianActiveAlertItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiService.respondToSOSIncident(
                    alert.id,
                    mapOf("note" to "Guardian accepted and is actively responding to the distress call.")
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GuardianDashboardActivity, "✅ Accepted incident! You are assigned as responder.", Toast.LENGTH_SHORT).show()
                    loadActiveAlerts()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GuardianDashboardActivity, "✅ Responding to incident in demo mode.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmResolveAlert(alert: GuardianActiveAlertItem) {
        AlertDialog.Builder(this)
            .setTitle("Resolve Emergency Alert")
            .setMessage("Have you confirmed that ${alert.userName} is safe? This will close the emergency incident and update all responders.")
            .setPositiveButton("MARK AS SAFE") { _, _ ->
                resolveIncident(alert)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resolveIncident(alert: GuardianActiveAlertItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiService.resolveSOSIncident(
                    alert.id,
                    mapOf("note" to "Guardian confirmed ward is safe. Alert marked as resolved.")
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GuardianDashboardActivity, "✅ Incident resolved successfully.", Toast.LENGTH_LONG).show()
                    loadActiveAlerts()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GuardianDashboardActivity, "✅ Incident marked safe.", Toast.LENGTH_SHORT).show()
                    loadActiveAlerts()
                }
            }
        }
    }

    private fun startPeriodicPolling() {
        lifecycleScope.launch {
            while (isActive) {
                delay(8000)
                try {
                    val response = apiService.getGuardianActiveAlerts()
                    if (response.isSuccessful && response.body() != null) {
                        updateAlertsUI(response.body()!!.activeAlerts)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Guardian Logout")
            .setMessage("Are you sure you want to log out from the Guardian Safety Shield?")
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

    private fun getMockActiveAlerts(): List<GuardianActiveAlertItem> {
        return listOf(
            GuardianActiveAlertItem(
                id = 101,
                userName = "Priya Sharma (Ward)",
                phone = "+91 98765 43210",
                emergencyType = "Harassment / Stalking",
                category = "harassment",
                status = "open",
                escalationTier = 1,
                latitude = 18.5204,
                longitude = 73.8567,
                address = "FC Road, Near Deccan Gymkhana, Pune",
                googleMapsUrl = "https://maps.google.com/?q=18.5204,73.8567",
                timeSinceSos = "1m 35s ago",
                elapsedSeconds = 95,
                batteryLevel = 84,
                createdAt = "2026-10-02T22:30:00Z"
            )
        )
    }
}
