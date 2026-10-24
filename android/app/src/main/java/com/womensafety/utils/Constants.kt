package com.womensafety.utils

object Constants {
    // 10.0.2.2 is the default IP address of the host machine when running on Android Emulator.
    // Replace with your local machine's IP (e.g. "http://192.168.1.5:8000/api/") when testing on physical device.
    const val BASE_URL = "http://10.0.2.2:8000/api/"

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
