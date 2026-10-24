package com.womensafety.ui.location

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.data.api.ApiService
import com.womensafety.databinding.ActivityLiveTrackingBinding
import com.womensafety.ui.MainActivity
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveTrackingBinding
    private lateinit var apiService: ApiService
    private lateinit var prefs: SharedPrefManager

    private var currentLat: Double = 19.8762
    private var currentLng: Double = 75.3433

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPrefManager.getInstance(this)
        apiService = ApiClient.getService(this)

        setupToolbar()
        setupMapWebView()
        setupActions()
        setupBottomNav()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMapWebView() {
        binding.mapWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        renderLeafletMap(currentLat, currentLng)
    }

    private fun renderLeafletMap(lat: Double, lng: Double) {
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0A0E21; }
                    .leaflet-tile { filter: brightness(0.6) invert(1) contrast(3) hue-rotate(200deg) saturate(0.3) brightness(0.7); }
                    .user-marker {
                        background: radial-gradient(circle, #FF2D7A 0%, #C70054 70%);
                        border: 3px solid #FFFFFF;
                        border-radius: 50%;
                        width: 22px;
                        height: 22px;
                        box-shadow: 0 0 15px rgba(255, 45, 122, 1);
                    }
                    .responder-marker {
                        background: radial-gradient(circle, #00E5FF 0%, #00B0FF 70%);
                        border: 2px solid #FFFFFF;
                        border-radius: 50%;
                        width: 18px;
                        height: 18px;
                        box-shadow: 0 0 12px rgba(0, 229, 255, 0.9);
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', { zoomControl: false }).setView([$lat, $lng], 15);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: ''
                    }).addTo(map);

                    var userIcon = L.divIcon({ className: 'user-marker', iconSize: [24, 24] });
                    var responderIcon = L.divIcon({ className: 'responder-marker', iconSize: [20, 20] });

                    var userMarker = L.marker([$lat, $lng], { icon: userIcon }).addTo(map).bindPopup("You");
                    var momMarker = L.marker([$lat + 0.008, $lng + 0.005], { icon: responderIcon }).addTo(map).bindPopup("Mom (On the way)");
                    var friendMarker = L.marker([$lat - 0.006, $lng - 0.007], { icon: responderIcon }).addTo(map).bindPopup("Friend (Nearby)");

                    // Draw route line to mom
                    var route = [[$lat, $lng], [$lat + 0.004, $lng + 0.003], [$lat + 0.008, $lng + 0.005]];
                    L.polyline(route, { color: '#00E5FF', weight: 4, opacity: 0.8, dashArray: '8, 8' }).addTo(map);

                    function recenter(newLat, newLng) {
                        map.panTo([newLat, newLng]);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        binding.mapWebView.loadDataWithBaseURL("https://openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
    }

    private fun setupActions() {
        binding.btnRecenter.setOnClickListener {
            binding.mapWebView.evaluateJavascript("if (typeof recenter === 'function') { recenter($currentLat, $currentLng); }", null)
            Toast.makeText(this, "Recentered on your location", Toast.LENGTH_SHORT).show()
        }

        binding.btnShareRoute.setOnClickListener {
            val mapsUrl = "https://www.google.com/maps?q=$currentLat,$currentLng"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SafeHer Live Location")
                putExtra(Intent.EXTRA_TEXT, "Track my live SafeHer location: $mapsUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Location via"))
        }
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.selectedItemId = R.id.nav_map
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_map -> true
                R.id.nav_alerts -> {
                    startActivity(Intent(this, com.womensafety.ui.alerts.AlertHistoryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    Toast.makeText(this, "Profile: ${prefs.getUserName()}", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }
}
