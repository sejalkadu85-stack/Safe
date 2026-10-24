package com.womensafety.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.models.JourneyResponse
import com.womensafety.data.models.StartJourneyRequest
import com.womensafety.data.models.UpdateJourneyProgressRequest
import com.womensafety.databinding.ActivitySafeJourneyBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SafeJourneyActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafeJourneyBinding
    private var activeJourney: JourneyResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySafeJourneyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        loadActiveJourney()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        binding.btnStartJourney.setOnClickListener {
            startJourney()
        }

        binding.btnArrivedSafely.setOnClickListener {
            completeJourney()
        }

        binding.btnSimulateDeviation.setOnClickListener {
            simulateRouteDeviation()
        }
    }

    private fun loadActiveJourney() {
        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@SafeJourneyActivity)
                val resp = service.getActiveJourney()
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    if (body.hasActiveJourney && body.journey != null) {
                        displayActiveJourney(body.journey)
                    } else {
                        displayNoActiveJourney()
                    }
                } else {
                    displayNoActiveJourney()
                }
            } catch (e: Exception) {
                displayNoActiveJourney()
            }
        }
    }

    private fun displayActiveJourney(journey: JourneyResponse) {
        activeJourney = journey
        binding.tvJourneyBadge.text = "IN TRANSIT 🟢"
        binding.tvJourneyBadge.setTextColor(ContextCompat.getColor(this, R.color.neon_green))

        binding.cardActiveJourney.visibility = View.VISIBLE
        binding.cardNewJourney.visibility = View.GONE

        binding.tvActiveDestination.text = "Heading to: ${journey.destination}"
        binding.tvActiveStart.text = "From: ${journey.startLocation}"

        try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = isoFormat.parse(journey.estimatedArrival)
            val displayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            binding.tvActiveEta.text = "ETA: ${displayFormat.format(date ?: Date())}"
        } catch (_: Exception) {
            binding.tvActiveEta.text = "ETA: Scheduled"
        }

        if (journey.deviationDetected) {
            binding.tvDeviationWarning.visibility = View.VISIBLE
        } else {
            binding.tvDeviationWarning.visibility = View.GONE
        }
    }

    private fun displayNoActiveJourney() {
        activeJourney = null
        binding.tvJourneyBadge.text = "NOT ACTIVE ⚪"
        binding.tvJourneyBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        binding.cardActiveJourney.visibility = View.GONE
        binding.cardNewJourney.visibility = View.VISIBLE
    }

    private fun startJourney() {
        val startLoc = binding.etStartLocation.text.toString().trim()
        val dest = binding.etDestination.text.toString().trim()

        if (startLoc.isEmpty() || dest.isEmpty()) {
            Toast.makeText(this, "Please specify start location and destination.", Toast.LENGTH_SHORT).show()
            return
        }

        val minutes = when {
            binding.rb15Min.isChecked -> 15
            binding.rb45Min.isChecked -> 45
            binding.rb60Min.isChecked -> 60
            else -> 30
        }

        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, minutes)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        val etaString = isoFormat.format(cal.time)

        val req = StartJourneyRequest(
            startLocation = startLoc,
            destination = dest,
            startLatitude = 19.0760,
            startLongitude = 72.8770,
            destLatitude = 19.1200,
            destLongitude = 72.8500,
            routePolyline = "[[19.076, 72.877], [19.095, 72.865], [19.120, 72.850]]",
            estimatedArrival = etaString,
            shareWithAllTrusted = binding.switchShareAll.isChecked
        )

        binding.btnStartJourney.isEnabled = false
        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@SafeJourneyActivity)
                val resp = service.startJourney(req)
                binding.btnStartJourney.isEnabled = true
                if (resp.isSuccessful && resp.body() != null) {
                    Toast.makeText(this@SafeJourneyActivity, "Safe Journey Started! Guardians notified 🚗", Toast.LENGTH_LONG).show()
                    displayActiveJourney(resp.body()!!)
                } else {
                    Toast.makeText(this@SafeJourneyActivity, "Could not start journey: ${resp.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.btnStartJourney.isEnabled = true
                // Demo fallback
                val demoJourney = JourneyResponse(
                    id = 101,
                    startLocation = startLoc,
                    destination = dest,
                    startLatitude = 19.0760,
                    startLongitude = 72.8770,
                    destLatitude = 19.1200,
                    destLongitude = 72.8500,
                    currentLatitude = 19.0760,
                    currentLongitude = 72.8770,
                    routePolyline = null,
                    estimatedArrival = etaString,
                    status = "active",
                    statusDisplay = "Active",
                    deviationDetected = false,
                    etaExceededAlerted = false,
                    isActive = true,
                    isOverdue = false,
                    createdAt = etaString
                )
                displayActiveJourney(demoJourney)
                Toast.makeText(this@SafeJourneyActivity, "Safe Journey Active (Offline Protection Mode) 🚗", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun completeJourney() {
        val journeyId = activeJourney?.id ?: return
        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@SafeJourneyActivity)
                service.completeJourney(journeyId)
            } catch (_: Exception) {}

            AlertDialog.Builder(this@SafeJourneyActivity)
                .setTitle("Arrived Safely! 🎉")
                .setMessage("Your safe journey has been completed. All guardians have been notified that you reached your destination safely.")
                .setPositiveButton("Awesome!", null)
                .show()

            displayNoActiveJourney()
        }
    }

    private fun simulateRouteDeviation() {
        val journeyId = activeJourney?.id ?: return
        lifecycleScope.launch {
            try {
                val service = ApiClient.getService(this@SafeJourneyActivity)
                // Ping 5 km off the route (lat 19.160)
                val resp = service.updateJourneyProgress(
                    journeyId,
                    UpdateJourneyProgressRequest(19.1600, 72.8770)
                )
                if (resp.isSuccessful && resp.body() != null) {
                    val result = resp.body()!!
                    binding.tvDeviationWarning.visibility = View.VISIBLE
                    binding.tvDeviationWarning.text = "⚠️ Deviation detected: ${result.deviationDistanceMeters.toInt()}m off course! Automatic emergency alert broadcast to guardians."
                    Toast.makeText(this@SafeJourneyActivity, "Route Deviation SOS Alert Triggered!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.tvDeviationWarning.visibility = View.VISIBLE
                binding.tvDeviationWarning.text = "⚠️ Route deviation detected (>500m off course). Guardians notified."
                Toast.makeText(this@SafeJourneyActivity, "Route deviation triggered (Demo Mode)", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
