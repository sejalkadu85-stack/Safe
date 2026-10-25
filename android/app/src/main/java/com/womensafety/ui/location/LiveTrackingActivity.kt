package com.womensafety.ui.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

        fetchCurrentGpsLocation()
        setupToolbar()
        setupMapWebView()
        setupActions()
        setupBottomNav()
    }

    private fun fetchCurrentGpsLocation() {
        try {
            val hasFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    currentLat = loc.latitude
                    currentLng = loc.longitude
                }
            }
        } catch (_: Exception) {}
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
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 SafeHerApp"
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        renderGoogleMap(currentLat, currentLng)
    }

    private fun renderGoogleMap(lat: Double, lng: Double) {
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #E5E3DF; }
                    
                    /* Clean Google Maps style pulse marker */
                    .gmap-user-container {
                        position: relative;
                        width: 28px;
                        height: 28px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .gmap-user-dot {
                        width: 16px;
                        height: 16px;
                        background: #1A73E8;
                        border: 3px solid #FFFFFF;
                        border-radius: 50%;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.4);
                        z-index: 2;
                        position: relative;
                    }
                    .gmap-user-radar {
                        position: absolute;
                        width: 38px;
                        height: 38px;
                        background: rgba(26, 115, 232, 0.28);
                        border-radius: 50%;
                        animation: radarPulse 2s infinite ease-out;
                        z-index: 1;
                    }
                    @keyframes radarPulse {
                        0% { transform: scale(0.5); opacity: 1; }
                        100% { transform: scale(1.6); opacity: 0; }
                    }

                    /* Responder Pin Badges */
                    .gmap-pin-badge {
                        display: flex;
                        align-items: center;
                        gap: 4px;
                        background: #FFFFFF;
                        color: #1F2937;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        font-size: 11px;
                        font-weight: 700;
                        padding: 3px 8px;
                        border-radius: 12px;
                        box-shadow: 0 3px 8px rgba(0,0,0,0.3);
                        white-space: nowrap;
                    }
                    .gmap-pin-badge.mom { border-left: 4px solid #FF007A; }
                    .gmap-pin-badge.friend { border-left: 4px solid #00B4D8; }

                    /* Google Maps style layer switch */
                    .leaflet-control-layers {
                        background: rgba(255, 255, 255, 0.95) !important;
                        border-radius: 8px !important;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.2) !important;
                        font-family: Roboto, Arial, sans-serif !important;
                        font-size: 11px !important;
                        border: none !important;
                        padding: 6px 10px !important;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([$lat, $lng], 15);

                    // Authentic Google Maps Roadmap layer
                    var googleRoadmap = L.tileLayer('https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
                    });

                    // Google Maps Satellite / Hybrid layer
                    var googleHybrid = L.tileLayer('https://{s}.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
                    });

                    // Clean Voyager fallback
                    var cartoVoyager = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                        maxZoom: 20
                    });

                    // Default to Google Maps Roadmap
                    googleRoadmap.addTo(map);

                    // Layer Switcher
                    var baseLayers = {
                        "Google Map": googleRoadmap,
                        "Satellite": googleHybrid,
                        "Terrain / Light": cartoVoyager
                    };
                    L.control.layers(baseLayers, null, { position: 'topleft' }).addTo(map);

                    // User Location Icon (Google Blue Dot)
                    var userIcon = L.divIcon({
                        className: '',
                        html: '<div class="gmap-user-container"><div class="gmap-user-radar"></div><div class="gmap-user-dot"></div></div>',
                        iconSize: [28, 28],
                        iconAnchor: [14, 14]
                    });

                    // Responder Badges
                    var momIcon = L.divIcon({
                        className: '',
                        html: '<div class="gmap-pin-badge mom">🛡️ Mom (On way)</div>',
                        iconSize: [110, 24],
                        iconAnchor: [55, 12]
                    });

                    var friendIcon = L.divIcon({
                        className: '',
                        html: '<div class="gmap-pin-badge friend">🤝 Friend (Nearby)</div>',
                        iconSize: [110, 24],
                        iconAnchor: [55, 12]
                    });

                    var userMarker = L.marker([$lat, $lng], { icon: userIcon }).addTo(map).bindPopup("<b>Your Live Location</b>");
                    var momMarker = L.marker([$lat + 0.007, $lng + 0.005], { icon: momIcon }).addTo(map).bindPopup("<b>Mom</b><br/>2.3 km away (Dispatched)");
                    var friendMarker = L.marker([$lat - 0.005, $lng - 0.006], { icon: friendIcon }).addTo(map).bindPopup("<b>Friend</b><br/>4.8 km away (Active)");

                    // Google Maps Navigation Polyline (Google Blue with soft glow)
                    var route = [[$lat, $lng], [$lat + 0.003, $lng + 0.002], [$lat + 0.007, $lng + 0.005]];
                    L.polyline(route, { color: '#1A73E8', weight: 5, opacity: 0.9 }).addTo(map);
                    L.polyline(route, { color: '#FFFFFF', weight: 2, opacity: 0.8, dashArray: '6, 6' }).addTo(map);

                    function recenter(newLat, newLng) {
                        map.flyTo([newLat, newLng], 16, { animate: true, duration: 1.2 });
                        userMarker.setLatLng([newLat, newLng]);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        binding.mapWebView.loadDataWithBaseURL("https://www.google.com", htmlContent, "text/html", "UTF-8", null)
    }

    private fun setupActions() {
        binding.btnRecenter.setOnClickListener {
            fetchCurrentGpsLocation()
            binding.mapWebView.evaluateJavascript("if (typeof recenter === 'function') { recenter($currentLat, $currentLng); }", null)
            Toast.makeText(this, "Recentered on your live location", Toast.LENGTH_SHORT).show()
        }

        binding.btnShareRoute.setOnClickListener {
            val mapsUrl = "https://www.google.com/maps?q=$currentLat,$currentLng"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SafeHer Live Location")
                putExtra(Intent.EXTRA_TEXT, "Track my live SafeHer location on Google Maps: $mapsUrl")
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

