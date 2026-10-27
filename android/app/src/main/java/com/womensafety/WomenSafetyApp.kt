package com.womensafety

import android.app.Application
import com.womensafety.utils.LocaleHelper
import com.womensafety.utils.SharedPrefManager

class WomenSafetyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize global singletons
        SharedPrefManager.getInstance(this)
        LocaleHelper.applyLocale(this)
    }
}
