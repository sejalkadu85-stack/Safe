package com.womensafety.utils

import android.content.Context
import android.content.SharedPreferences

class SharedPrefManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveAuthTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(Constants.KEY_ACCESS_TOKEN, accessToken)
            .putString(Constants.KEY_REFRESH_TOKEN, refreshToken)
            .putBoolean(Constants.KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun saveUserData(userId: Int, username: String, email: String, role: String) {
        prefs.edit()
            .putInt(Constants.KEY_USER_ID, userId)
            .putString(Constants.KEY_USER_NAME, username)
            .putString(Constants.KEY_USER_EMAIL, email)
            .putString(Constants.KEY_USER_ROLE, role)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(Constants.KEY_REFRESH_TOKEN, null)
    fun isLoggedIn(): Boolean = prefs.getBoolean(Constants.KEY_IS_LOGGED_IN, false)
    fun getUserRole(): String? = prefs.getString(Constants.KEY_USER_ROLE, Constants.ROLE_WOMAN)
    fun getUserId(): Int = prefs.getInt(Constants.KEY_USER_ID, -1)
    fun getUserName(): String? = prefs.getString(Constants.KEY_USER_NAME, "")
    fun getUserEmail(): String? = prefs.getString(Constants.KEY_USER_EMAIL, "")

    fun saveUserPhone(phone: String) {
        prefs.edit().putString("user_phone", phone).apply()
    }
    fun getUserPhone(): String? = prefs.getString("user_phone", "")

    fun saveUserPhoto(photoUrl: String) {
        prefs.edit().putString("user_photo_url", photoUrl).apply()
    }
    fun getUserPhoto(): String? = prefs.getString("user_photo_url", null)

    fun saveUserFullName(fullName: String) {
        prefs.edit().putString("user_full_name", fullName).apply()
    }
    fun getUserFullName(): String? = prefs.getString("user_full_name", null)

    // FCM Device Token
    fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_device_token", token).apply()
    }
    fun getFcmToken(): String? = prefs.getString("fcm_device_token", null)

    // Notification Preferences
    fun setSosNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("pref_notif_sos", enabled).apply()
    fun isSosNotificationEnabled(): Boolean = prefs.getBoolean("pref_notif_sos", true)

    fun setLocationNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("pref_notif_location", enabled).apply()
    fun isLocationNotificationEnabled(): Boolean = prefs.getBoolean("pref_notif_location", true)

    fun setChatNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("pref_notif_chat", enabled).apply()
    fun isChatNotificationEnabled(): Boolean = prefs.getBoolean("pref_notif_chat", true)

    fun setContactNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("pref_notif_contact", enabled).apply()
    fun isContactNotificationEnabled(): Boolean = prefs.getBoolean("pref_notif_contact", true)

    fun setCheckInNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("pref_notif_checkin", enabled).apply()
    fun isCheckInNotificationEnabled(): Boolean = prefs.getBoolean("pref_notif_checkin", true)

    fun setVibrationEnabled(enabled: Boolean) = prefs.edit().putBoolean("pref_notif_vibrate", enabled).apply()
    fun isVibrationEnabled(): Boolean = prefs.getBoolean("pref_notif_vibrate", true)

    fun setSoundEnabled(enabled: Boolean) = prefs.edit().putBoolean("pref_notif_sound", enabled).apply()
    fun isSoundEnabled(): Boolean = prefs.getBoolean("pref_notif_sound", true)

    // Last known location (for battery-low alert)
    fun saveLastLocation(latitude: Double, longitude: Double, address: String = "") {
        prefs.edit()
            .putFloat("last_latitude", latitude.toFloat())
            .putFloat("last_longitude", longitude.toFloat())
            .putString("last_address", address)
            .apply()
    }
    fun getLastLatitude(): Double? {
        val v = prefs.getFloat("last_latitude", Float.MIN_VALUE)
        return if (v == Float.MIN_VALUE) null else v.toDouble()
    }
    fun getLastLongitude(): Double? {
        val v = prefs.getFloat("last_longitude", Float.MIN_VALUE)
        return if (v == Float.MIN_VALUE) null else v.toDouble()
    }
    fun getLastAddress(): String? = prefs.getString("last_address", null)

    // Emergency Contacts & Primary Guardian Cache
    fun savePrimaryGuardianPhone(phone: String) {
        prefs.edit().putString("primary_guardian_phone", phone).apply()
    }
    fun getPrimaryGuardianPhone(): String? = prefs.getString("primary_guardian_phone", null)

    fun saveEmergencyNumbers(numbers: Set<String>) {
        prefs.edit().putStringSet("emergency_numbers_set", numbers).apply()
    }
    fun getEmergencyNumbers(): Set<String> = prefs.getStringSet("emergency_numbers_set", emptySet()) ?: emptySet()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        @Volatile
        private var instance: SharedPrefManager? = null

        fun getInstance(context: Context): SharedPrefManager {
            return instance ?: synchronized(this) {
                instance ?: SharedPrefManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
