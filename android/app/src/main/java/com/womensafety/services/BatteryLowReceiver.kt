package com.womensafety.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.womensafety.data.api.ApiClient
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Task 5.4 — Battery-Low Auto Alert
 *
 * Listens for system ACTION_BATTERY_CHANGED broadcasts.
 * When battery level drops below 15%, sends the last known location
 * to all trusted contacts via the backend API.
 *
 * Register in AndroidManifest with:
 *   <receiver android:name=".services.BatteryLowReceiver" android:exported="false">
 *       <intent-filter>
 *           <action android:name="android.intent.action.BATTERY_LOW" />
 *       </intent-filter>
 *   </receiver>
 */
class BatteryLowReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BatteryLowReceiver"
        private const val BATTERY_THRESHOLD = 15
        // Track last alert time to avoid spamming (alert at most every 30 min)
        private var lastAlertTimestamp = 0L
        private const val ALERT_COOLDOWN_MS = 30 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_BATTERY_LOW && action != Intent.ACTION_BATTERY_CHANGED) return

        val prefs = SharedPrefManager.getInstance(context)
        // Only send if user is logged in
        if (!prefs.isLoggedIn()) return
        val token = prefs.getAccessToken()
        if (token.isNullOrEmpty() || token.startsWith("demo_")) return

        val now = System.currentTimeMillis()
        if (now - lastAlertTimestamp < ALERT_COOLDOWN_MS) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = if (scale > 0) (level * 100) / scale else level

        if (batteryPct > BATTERY_THRESHOLD) return

        Log.d(TAG, "Battery low: $batteryPct% — sending auto alert")
        lastAlertTimestamp = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = mapOf<String, Any>(
                    "battery_level" to batteryPct,
                    "latitude" to (prefs.getLastLatitude() ?: 0.0),
                    "longitude" to (prefs.getLastLongitude() ?: 0.0),
                    "address" to (prefs.getLastAddress() ?: "")
                )
                val response = ApiClient.getService(context).sendBatteryLowAlert(body)
                if (response.isSuccessful) {
                    Log.d(TAG, "Battery low alert sent successfully: ${response.body()}")
                } else {
                    Log.w(TAG, "Battery low alert failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Battery low alert error: ${e.message}")
            }
        }
    }
}
