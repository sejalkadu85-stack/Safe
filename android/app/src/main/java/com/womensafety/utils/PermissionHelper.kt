package com.womensafety.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper class for managing runtime permissions needed by SafeHer.
 *
 * Covers:
 * - Location (Fine, Coarse, Background)
 * - Microphone (Audio recording for AI monitor)
 * - Camera (Evidence recording)
 * - Contacts (Trusted contact lookup)
 * - Notifications (POST_NOTIFICATIONS on Android 13+)
 * - Phone Call (Emergency dial)
 * - Battery optimization whitelist
 */
object PermissionHelper {

    // Permission groups
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val BACKGROUND_LOCATION = arrayOf(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    val MICROPHONE_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    val CAMERA_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA
    )

    val CONTACTS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CONTACTS
    )

    val CALL_PERMISSIONS = arrayOf(
        Manifest.permission.CALL_PHONE
    )

    // Core permissions needed at startup
    val ONBOARDING_PERMISSIONS: Array<String>
        get() {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CAMERA,
                Manifest.permission.SEND_SMS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms.toTypedArray()
        }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { hasPermission(context, it) }
    }

    fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else true
    }

    fun hasMicrophonePermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.RECORD_AUDIO)

    fun hasCameraPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.CAMERA)

    fun hasContactsPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.READ_CONTACTS)

    fun hasCallPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.CALL_PHONE)

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else true
    }

    /**
     * Request multiple permissions with a single call.
     */
    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    /**
     * Show rationale dialog before requesting a permission.
     */
    fun showRationaleDialog(
        activity: Activity,
        title: String,
        message: String,
        onProceed: () -> Unit,
        onDeny: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Allow") { _, _ -> onProceed() }
            .setNegativeButton("Not Now") { _, _ -> onDeny?.invoke() }
            .setCancelable(false)
            .show()
    }

    /**
     * Show dialog to open Settings when permission was permanently denied.
     */
    fun showSettingsDialog(activity: Activity, permissionName: String) {
        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage("$permissionName permission was denied. Please enable it in Settings to use this feature.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Check if the app is whitelisted from battery optimization.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Prompt user to disable battery optimization (required for background SOS & check-in).
     */
    fun requestBatteryOptimizationWhitelist(activity: Activity) {
        if (!isBatteryOptimizationIgnored(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("⚡ Background Activity Needed")
                .setMessage(
                    "To ensure SOS alerts and check-in timers work reliably in the background, " +
                    "please allow SafeHer to run without battery restrictions.\n\n" +
                    "Tap 'Allow' and select 'Don't optimize' from the list."
                )
                .setPositiveButton("Allow") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback: open battery settings
                        activity.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                    }
                }
                .setNegativeButton("Skip", null)
                .show()
        }
    }

    /**
     * Show a comprehensive onboarding permission flow.
     * Call this in OnboardingActivity or after first login.
     */
    fun runOnboardingPermissionFlow(
        activity: Activity,
        requestCode: Int = 1001
    ) {
        val missing = ONBOARDING_PERMISSIONS.filter { !hasPermission(activity, it) }
        if (missing.isEmpty()) {
            // All granted — also check battery
            requestBatteryOptimizationWhitelist(activity)
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("🛡️ SafeHer Needs These Permissions")
            .setMessage(
                "For your safety features to work properly, SafeHer needs:\n\n" +
                "📍 Location — For SOS & live tracking\n" +
                "🎤 Microphone — For AI distress detection\n" +
                "📷 Camera — For evidence recording\n" +
                "👥 Contacts — To find trusted contacts\n" +
                "📞 Phone — For emergency calling\n" +
                "🔔 Notifications — For SOS alerts"
            )
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestPermissions(activity, missing.toTypedArray(), requestCode)
            }
            .setNegativeButton("Skip") { _, _ ->
                // partial permissions — app still works with limited features
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Process results from onRequestPermissionsResult.
     * Returns true if all requested permissions were granted.
     */
    fun processPermissionResult(
        grantResults: IntArray,
        onAllGranted: () -> Unit,
        onPartiallyDenied: (List<String>) -> Unit,
        requestedPermissions: Array<String>
    ) {
        val denied = mutableListOf<String>()
        for (i in grantResults.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                denied.add(requestedPermissions.getOrNull(i) ?: "Unknown")
            }
        }
        if (denied.isEmpty()) {
            onAllGranted()
        } else {
            onPartiallyDenied(denied)
        }
    }

    // Friendly names for permissions
    fun friendlyName(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background Location"
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.READ_CONTACTS -> "Contacts"
            Manifest.permission.CALL_PHONE -> "Phone"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            Manifest.permission.SEND_SMS -> "SMS"
            else -> permission.substringAfterLast(".")
        }
    }
}
