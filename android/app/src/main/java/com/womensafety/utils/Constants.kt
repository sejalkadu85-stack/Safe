package com.womensafety.utils

import android.content.Context
import android.os.Build

object Constants {
    // Standard hosts for testing environments:
    // 10.0.2.2:8000 -> Android Virtual Device / Pixel Emulator loopback
    // 10.223.139.210:8000 -> Current Host PC Wi-Fi LAN IP (for physical mobile over Wi-Fi)
    // 127.0.0.1:8000 -> Physical mobile connected via USB with "adb reverse tcp:8000 tcp:8000"
    const val DEFAULT_EMULATOR_HOST = "10.0.2.2:8000"
    const val DEFAULT_LAN_HOST = "10.223.139.210:8000"
    const val USB_REVERSE_HOST = "127.0.0.1:8000"

    const val KEY_CUSTOM_SERVER_HOST = "custom_server_host"

    /**
     * Determines if the current runtime is an Android Emulator or a physical device.
     */
    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        val device = Build.DEVICE
        val product = Build.PRODUCT
        val hardware = Build.HARDWARE

        return (fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk", ignoreCase = true)
                || model.contains("Emulator", ignoreCase = true)
                || model.contains("Android SDK built for x86", ignoreCase = true)
                || manufacturer.contains("Genymotion", ignoreCase = true)
                || hardware.contains("goldfish", ignoreCase = true)
                || hardware.contains("ranchu", ignoreCase = true)
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || "google_sdk" == product)
    }

    /**
     * Returns the target host & port (e.g. "10.223.139.210:8000" or "10.0.2.2:8000").
     */
    fun getServerHost(context: Context? = null): String {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_CUSTOM_SERVER_HOST, null)
            if (!custom.isNullOrBlank()) {
                return custom.trim()
            }
        }
        return if (isEmulator()) DEFAULT_EMULATOR_HOST else DEFAULT_LAN_HOST
    }

    /**
     * Saves user-configured server host in SharedPreferences.
     */
    fun setCustomServerHost(context: Context, host: String) {
        val clean = host.removePrefix("http://").removePrefix("https://").removeSuffix("/api/").removeSuffix("/")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_SERVER_HOST, clean.trim())
            .apply()
    }

    /**
     * Returns the full HTTP API base URL ending with "/api/".
     */
    fun getBaseUrl(context: Context? = null): String {
        val host = getServerHost(context)
        val cleanHost = host.removePrefix("http://").removePrefix("https://").removeSuffix("/")
        val protocol = if (host.startsWith("https://")) "https://" else "http://"
        return "$protocol$cleanHost/api/"
    }

    // Dynamic property for backward-compatibility with direct Constants.BASE_URL references
    val BASE_URL: String
        get() = getBaseUrl()

    // User Roles matching Django CustomUser.Role
    const val ROLE_WOMAN = "woman"
    const val ROLE_GUARDIAN = "guardian"
    const val ROLE_FRIEND = "friend"
    const val ROLE_VOLUNTEER = "volunteer"
    const val ROLE_ADMIN = "admin"

    // SharedPreferences Keys
    const val PREFS_NAME = "women_safety_prefs"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_USER_ROLE = "user_role"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
}
