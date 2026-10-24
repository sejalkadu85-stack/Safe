package com.womensafety.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.womensafety.R
import com.womensafety.data.api.ApiClient
import com.womensafety.ui.MainActivity
import com.womensafety.ui.chat.ChatActivity
import com.womensafety.ui.contacts.TrustedContactsActivity
import com.womensafety.ui.location.LiveTrackingActivity
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SafetyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SafetyFCM"

        const val CHANNEL_EMERGENCY = "channel_emergency_alerts"
        const val CHANNEL_DEFAULT = "channel_safety_notifications"
        const val CHANNEL_CHAT = "channel_chat_messages"

        fun registerTokenWithBackend(context: Context, token: String? = null) {
            val prefs = SharedPrefManager.getInstance(context)
            if (!prefs.isLoggedIn()) return

            val fcmToken = token ?: prefs.getFcmToken() ?: return

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val payload = mapOf(
                        "token" to fcmToken,
                        "device_type" to "android",
                        "device_name" to "${Build.MANUFACTURER} ${Build.MODEL}"
                    )
                    val response = ApiClient.getService(context).registerDeviceToken(payload)
                    if (response.isSuccessful) {
                        Log.d(TAG, "Device token registered with backend successfully: ${fcmToken.take(15)}...")
                    } else {
                        Log.w(TAG, "Failed to register token: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering token with backend: ${e.message}")
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token generated: $token")
        SharedPrefManager.getInstance(this).saveFcmToken(token)
        registerTokenWithBackend(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val title = remoteMessage.notification?.title ?: data["title"] ?: "Safety Alert"
        val body = remoteMessage.notification?.body ?: data["body"] ?: "New notification received"
        val notifType = data["notification_type"] ?: "general"

        val prefs = SharedPrefManager.getInstance(this)

        // Check user preferences
        if (!shouldShowNotification(prefs, notifType)) {
            Log.d(TAG, "Notification skipped due to user preference: $notifType")
            return
        }

        createNotificationChannels()
        showNotification(title, body, notifType, data)
    }

    private fun shouldShowNotification(prefs: SharedPrefManager, type: String): Boolean {
        return when (type) {
            "sos_alert" -> true // SOS alerts always enabled for safety
            "location_share" -> prefs.isLocationNotificationEnabled()
            "new_message" -> prefs.isChatNotificationEnabled()
            "contact_request" -> prefs.isContactNotificationEnabled()
            "check_in" -> prefs.isCheckInNotificationEnabled()
            else -> true
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Emergency Channel (SOS)
            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent high-priority life safety and emergency alerts"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // 2. Chat Channel
            val chatChannel = NotificationChannel(
                CHANNEL_CHAT,
                "Safety Chats & Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Guardian and volunteer in-app messages"
                enableLights(true)
                lightColor = Color.CYAN
            }

            // 3. Default Channel
            val defaultChannel = NotificationChannel(
                CHANNEL_DEFAULT,
                "General Safety Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Location shares, contact requests, and check-ins"
            }

            manager.createNotificationChannel(emergencyChannel)
            manager.createNotificationChannel(chatChannel)
            manager.createNotificationChannel(defaultChannel)
        }
    }

    private fun showNotification(title: String, body: String, notifType: String, data: Map<String, String>) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = System.currentTimeMillis().toInt()

        val isEmergency = notifType == "sos_alert"
        val channelId = when (notifType) {
            "sos_alert" -> CHANNEL_EMERGENCY
            "new_message" -> CHANNEL_CHAT
            else -> CHANNEL_DEFAULT
        }

        // Deep Link Target Intent
        val contentIntent = createDeepLinkIntent(notifType, data)
        val pendingContentIntent = PendingIntent.getActivity(
            this,
            notifId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_safety_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingContentIntent)
            .setAutoCancel(true)
            .setPriority(if (isEmergency) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)

        // SOS Alert Custom Actions: Full Screen Intent + Map Link + Call
        if (isEmergency) {
            builder.setColor(Color.RED)
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)

            // Full screen intent for lock-screen heads up
            builder.setFullScreenIntent(pendingContentIntent, true)

            // Action: View on Map
            val mapIntent = Intent(this, LiveTrackingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mapPendingIntent = PendingIntent.getActivity(
                this,
                notifId + 1,
                mapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_location_pin, "View on Map 🗺️", mapPendingIntent)

            // Action: Emergency Call
            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
            val callPendingIntent = PendingIntent.getActivity(
                this,
                notifId + 2,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_phone_neon, "Call 112 📞", callPendingIntent)
        } else if (notifType == "new_message") {
            builder.setColor(Color.CYAN)
            builder.addAction(R.drawable.ic_nav_chat, "Reply 💬", pendingContentIntent)
        } else if (notifType == "check_in") {
            builder.setColor(Color.GREEN)
            val safeIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action_respond_safe", true)
            }
            val safePendingIntent = PendingIntent.getActivity(
                this,
                notifId + 3,
                safeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_safety_shield, "I'm Safe ✅", safePendingIntent)
        }

        manager.notify(notifId, builder.build())
    }

    private fun createDeepLinkIntent(notifType: String, data: Map<String, String>): Intent {
        return when (notifType) {
            "sos_alert" -> {
                Intent(this, LiveTrackingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("extra_sos_alert", true)
                    putExtra("extra_lat", data["latitude"]?.toDoubleOrNull() ?: 28.6139)
                    putExtra("extra_lng", data["longitude"]?.toDoubleOrNull() ?: 77.2090)
                }
            }
            "location_share" -> {
                Intent(this, LiveTrackingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "new_message" -> {
                Intent(this, ChatActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    val convId = data["conversation_id"]?.toIntOrNull() ?: -1
                    putExtra(ChatActivity.EXTRA_CONVERSATION_ID, convId)
                    putExtra(ChatActivity.EXTRA_CONTACT_NAME, data["sender_username"] ?: "Guardian")
                }
            }
            "contact_request" -> {
                Intent(this, TrustedContactsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "check_in" -> {
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("action_check_in_dialog", true)
                }
            }
            else -> {
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }
    }
}
