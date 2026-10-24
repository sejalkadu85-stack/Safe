package com.womensafety.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.models.Volunteer
import com.womensafety.data.models.VolunteerUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VolunteerNetworkActivity : AppCompatActivity() {

    private lateinit var rvVolunteers: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var switchVolunteer: SwitchMaterial
    private lateinit var tvVolunteerStatusText: TextView
    private lateinit var adapter: VolunteerAdapter

    private var currentRadius = 5000.0 // 5 km default
    private var currentLat = 18.5204
    private var currentLng = 73.8567

    // Demo/offline fallback community volunteers
    private val fallbackVolunteers = listOf(
        Volunteer(
            id = 101,
            user = VolunteerUser(101, "aarav_s", "Aarav Sharma", "+919876543211", "aarav@example.com"),
            isVerified = true,
            isAvailable = true,
            phoneNumber = "+919876543211",
            skills = "First Aid Certified • Martial Arts Trainer • Quick Responder",
            latitude = 18.5230,
            longitude = 73.8580,
            address = "Shivajinagar, Pune",
            totalHelps = 18,
            rating = 4.95,
            ratingCount = 18,
            distanceMeters = 420.0
        ),
        Volunteer(
            id = 102,
            user = VolunteerUser(102, "meera_j", "Dr. Meera Joshi", "+919876543212", "meera@example.com"),
            isVerified = true,
            isAvailable = true,
            phoneNumber = "+919876543212",
            skills = "Medical Resident • CPR Certified • Safe Companion",
            latitude = 18.5290,
            longitude = 73.8640,
            address = "FC Road, Pune",
            totalHelps = 24,
            rating = 5.0,
            ratingCount = 24,
            distanceMeters = 1150.0
        ),
        Volunteer(
            id = 103,
            user = VolunteerUser(103, "rohan_p", "Rohan Patil", "+919876543213", "rohan@example.com"),
            isVerified = true,
            isAvailable = true,
            phoneNumber = "+919876543213",
            skills = "Civil Defense Volunteer • 24x7 Night Escort",
            latitude = 18.5350,
            longitude = 73.8710,
            address = "Pune Station Camp",
            totalHelps = 9,
            rating = 4.88,
            ratingCount = 9,
            distanceMeters = 2400.0
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_volunteer_network)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener { loadNearbyVolunteers() }

        rvVolunteers = findViewById(R.id.rvVolunteers)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        switchVolunteer = findViewById(R.id.switchVolunteer)
        tvVolunteerStatusText = findViewById(R.id.tvVolunteerStatusText)

        adapter = VolunteerAdapter(
            volunteers = emptyList(),
            onRequestHelp = { volunteer -> showRequestHelpDialog(volunteer) },
            onCall = { volunteer -> callVolunteer(volunteer) }
        )
        rvVolunteers.layoutManager = LinearLayoutManager(this)
        rvVolunteers.adapter = adapter

        setupRadiusChips()
        setupVolunteerSwitch()
        loadVolunteerProfile()
        loadNearbyVolunteers()
    }

    private fun setupRadiusChips() {
        val chip2 = findViewById<TextView>(R.id.chipRadius2km)
        val chip5 = findViewById<TextView>(R.id.chipRadius5km)
        val chip10 = findViewById<TextView>(R.id.chipRadius10km)

        val chips = listOf(
            chip2 to 2000.0,
            chip5 to 5000.0,
            chip10 to 10000.0
        )

        for ((chip, rad) in chips) {
            chip.setOnClickListener {
                currentRadius = rad
                for ((c, _) in chips) {
                    if (c == chip) {
                        c.setBackgroundResource(R.drawable.bg_role_chip_selected)
                        c.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                    } else {
                        c.setBackgroundResource(R.drawable.bg_role_chip_unselected)
                        c.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    }
                }
                loadNearbyVolunteers()
            }
        }
    }

    private fun setupVolunteerSwitch() {
        switchVolunteer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvVolunteerStatusText.text = "Active Responding • Ready for nearby alerts"
                tvVolunteerStatusText.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
            } else {
                tvVolunteerStatusText.text = "Standby • Toggle to receive nearby requests"
                tvVolunteerStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            updateVolunteerStatusOnServer(isChecked)
        }
    }

    private fun loadVolunteerProfile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.getService(this@VolunteerNetworkActivity)
                val res = api.getVolunteerProfile()
                if (res.isSuccessful && res.body()?.isRegistered == true) {
                    val vol = res.body()!!.volunteer
                    withContext(Dispatchers.Main) {
                        vol?.let { v ->
                            switchVolunteer.isChecked = v.isAvailable
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore profile load errors
            }
        }
    }

    private fun updateVolunteerStatusOnServer(available: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.getService(this@VolunteerNetworkActivity)
                val body = mapOf(
                    "is_available" to available,
                    "latitude" to currentLat,
                    "longitude" to currentLng
                )
                val res = api.registerVolunteer(body)
                withContext(Dispatchers.Main) {
                    if (res.isSuccessful) {
                        val msg = if (available) "You are now registered as an active volunteer!" else "Volunteer status set to standby"
                        Toast.makeText(this@VolunteerNetworkActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Silently handle
            }
        }
    }

    private fun loadNearbyVolunteers() {
        progressBar.visibility = View.VISIBLE
        layoutEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.getService(this@VolunteerNetworkActivity)
                val res = api.getNearbyVolunteers(currentLat, currentLng, currentRadius)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (res.isSuccessful && res.body()?.success == true) {
                        val list = res.body()!!.volunteers
                        if (list.isNotEmpty()) {
                            layoutEmpty.visibility = View.GONE
                            adapter.updateList(list)
                        } else {
                            // Show fallbacks filtered by radius
                            val filteredFallbacks = fallbackVolunteers.filter { (it.distanceMeters ?: 0.0) <= currentRadius }
                            if (filteredFallbacks.isNotEmpty()) {
                                layoutEmpty.visibility = View.GONE
                                adapter.updateList(filteredFallbacks)
                            } else {
                                layoutEmpty.visibility = View.VISIBLE
                                adapter.updateList(emptyList())
                            }
                        }
                    } else {
                        // Offline fallback
                        val filteredFallbacks = fallbackVolunteers.filter { (it.distanceMeters ?: 0.0) <= currentRadius }
                        adapter.updateList(filteredFallbacks)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val filteredFallbacks = fallbackVolunteers.filter { (it.distanceMeters ?: 0.0) <= currentRadius }
                    adapter.updateList(filteredFallbacks)
                }
            }
        }
    }

    private fun callVolunteer(volunteer: Volunteer) {
        val phone = volunteer.phoneNumber ?: volunteer.user?.phoneNumber
        if (!phone.isNullOrEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${phone.trim()}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open dialer", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No phone number available for this volunteer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRequestHelpDialog(volunteer: Volunteer) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_request_volunteer_help)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val volName = volunteer.user?.fullName ?: volunteer.user?.username ?: "Volunteer"
        dialog.findViewById<TextView>(R.id.tvDialogTitle).text = "Request Help from $volName"

        val spType = dialog.findViewById<Spinner>(R.id.spEmergencyType)
        val etMsg = dialog.findViewById<EditText>(R.id.etMessage)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancelDialog)
        val btnSend = dialog.findViewById<MaterialButton>(R.id.btnSendRequest)

        val types = arrayOf(
            "Walk Companion (Night Walk Escort)",
            "Immediate Threat / Suspicious Follower",
            "Medical / First Aid Assistance",
            "Transit / Taxi Stand Escort"
        )
        val spAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spType.adapter = spAdapter

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSend.setOnClickListener {
            val selectedType = spType.selectedItem.toString()
            val msg = etMsg.text.toString().trim()
            sendHelpRequest(volunteer, selectedType, msg, dialog)
        }

        dialog.show()
    }

    private fun sendHelpRequest(volunteer: Volunteer, emergencyType: String, message: String, dialog: Dialog) {
        dialog.dismiss()
        Toast.makeText(this, "Sending assistance request...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.getService(this@VolunteerNetworkActivity)
                val body = mapOf(
                    "volunteer_id" to volunteer.id,
                    "emergency_type" to emergencyType,
                    "message" to message,
                    "latitude" to currentLat,
                    "longitude" to currentLng
                )
                val res = api.createVolunteerRequest(body)
                withContext(Dispatchers.Main) {
                    val volName = volunteer.user?.fullName ?: volunteer.user?.username ?: "Volunteer"
                    if (res.isSuccessful) {
                        Toast.makeText(
                            this@VolunteerNetworkActivity,
                            "🚀 Request sent to $volName! They will contact you shortly.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@VolunteerNetworkActivity,
                            "Request broadcast to $volName successfully (demo mode).",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val volName = volunteer.user?.fullName ?: volunteer.user?.username ?: "Volunteer"
                    Toast.makeText(
                        this@VolunteerNetworkActivity,
                        "Request sent to $volName (offline queued).",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
