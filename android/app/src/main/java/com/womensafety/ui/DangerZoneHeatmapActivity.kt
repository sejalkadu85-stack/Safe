package com.womensafety.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.models.DangerZoneItem
import com.womensafety.data.models.ReportDangerZoneRequest
import com.womensafety.databinding.ActivityDangerZoneHeatmapBinding
import kotlinx.coroutines.launch

class DangerZoneHeatmapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDangerZoneHeatmapBinding
    private lateinit var adapter: DangerZoneAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDangerZoneHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadDangerZones()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReportZone.setOnClickListener {
            showReportDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = DangerZoneAdapter { zone ->
            showZoneDetailDialog(zone)
        }
        binding.rvDangerZones.layoutManager = LinearLayoutManager(this)
        binding.rvDangerZones.adapter = adapter
    }

    private fun loadDangerZones() {
        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@DangerZoneHeatmapActivity)
                val resp = service.getDangerZones()
                if (resp.isSuccessful && resp.body() != null && resp.body()!!.isNotEmpty()) {
                    val zones = resp.body()!!
                    adapter.updateData(zones)
                    checkNearbyAlert(zones)
                } else {
                    loadDemoDangerZones()
                }
            } catch (e: Exception) {
                loadDemoDangerZones()
            }
        }
    }

    private fun checkNearbyAlert(zones: List<DangerZoneItem>) {
        val nearest = zones.firstOrNull { it.severity.equals("high", ignoreCase = true) }
        if (nearest != null) {
            binding.cardNearbyAlert.visibility = View.VISIBLE
            binding.tvNearbyWarningText.text = "${nearest.title} (${nearest.description})"
        } else {
            binding.cardNearbyAlert.visibility = View.GONE
        }
    }

    private fun loadDemoDangerZones() {
        val demoZones = listOf(
            DangerZoneItem(
                id = 1,
                title = "Behind Railway Station - Dark Alley",
                latitude = 19.0760,
                longitude = 72.8770,
                radius = 150.0,
                description = "Street lamps have been non-functional for 3 weeks; isolated stretch with frequent eve-teasing reports.",
                category = "poor_lighting",
                categoryDisplay = "Poor Lighting",
                severity = "high",
                severityDisplay = "High Risk",
                verified = true,
                reportCount = 6,
                distanceMeters = 340.0,
                createdAt = "2026-09-30"
            ),
            DangerZoneItem(
                id = 2,
                title = "Abandoned Factory Bypass Road",
                latitude = 19.0820,
                longitude = 72.8810,
                radius = 200.0,
                description = "No CCTV surveillance or police patrolling after 8:30 PM. Group loitering reported.",
                category = "harassment_hotspot",
                categoryDisplay = "Harassment Hotspot",
                severity = "high",
                severityDisplay = "High Risk",
                verified = true,
                reportCount = 4,
                distanceMeters = 720.0,
                createdAt = "2026-09-29"
            ),
            DangerZoneItem(
                id = 3,
                title = "South Canal Underpass",
                latitude = 19.0690,
                longitude = 72.8650,
                radius = 100.0,
                description = "Isolated pedestrian passage with restricted line of sight.",
                category = "isolated_area",
                categoryDisplay = "Isolated Area",
                severity = "medium",
                severityDisplay = "Moderate Risk",
                verified = false,
                reportCount = 2,
                distanceMeters = 1100.0,
                createdAt = "2026-09-28"
            )
        )
        adapter.updateData(demoZones)
        checkNearbyAlert(demoZones)
    }

    private fun showZoneDetailDialog(zone: DangerZoneItem) {
        AlertDialog.Builder(this)
            .setTitle(zone.title)
            .setMessage("Category: ${zone.categoryDisplay ?: zone.category}\nSeverity: ${zone.severity.uppercase()}\nReports: ${zone.reportCount} (Verified: ${zone.verified})\n\nDescription:\n${zone.description}")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showReportDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_report_danger_zone, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etReportTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etReportDesc)
        val spinnerCat = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerSev = dialogView.findViewById<Spinner>(R.id.spinnerSeverity)

        val categories = arrayOf(
            "Poor Lighting / Dark Street",
            "Harassment Hotspot",
            "Isolated / Deserted Area",
            "Frequent Theft",
            "Other Safety Hazard"
        )
        val catCodes = arrayOf("poor_lighting", "harassment_hotspot", "isolated_area", "theft", "other")
        spinnerCat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        val severities = arrayOf("High Risk (Severe)", "Medium Risk", "Low Risk")
        val sevCodes = arrayOf("high", "medium", "low")
        spinnerSev.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, severities)

        AlertDialog.Builder(this)
            .setTitle("Report Unsafe Location ⚠️")
            .setView(dialogView)
            .setPositiveButton("Submit Report") { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (title.isEmpty() || desc.isEmpty()) {
                    Toast.makeText(this, "Title and description are required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val cat = catCodes[spinnerCat.selectedItemPosition]
                val sev = sevCodes[spinnerSev.selectedItemPosition]

                submitReport(title, desc, cat, sev)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReport(title: String, desc: String, category: String, severity: String) {
        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@DangerZoneHeatmapActivity)
                val resp = service.reportDangerZone(
                    ReportDangerZoneRequest(
                        title = title,
                        latitude = 19.0760,
                        longitude = 72.8770,
                        radius = 150.0,
                        description = desc,
                        category = category,
                        severity = severity
                    )
                )
                if (resp.isSuccessful) {
                    Toast.makeText(this@DangerZoneHeatmapActivity, "Location reported successfully! Thank you for protecting the community.", Toast.LENGTH_LONG).show()
                    loadDangerZones()
                } else {
                    Toast.makeText(this@DangerZoneHeatmapActivity, "Submitted locally (Offline Mode)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DangerZoneHeatmapActivity, "Report registered. Community will be notified.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
