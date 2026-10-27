package com.womensafety.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.womensafety.R
import java.util.Locale

object LocaleHelper {

    const val LANG_EN = "en"
    const val LANG_MR = "mr"
    const val LANG_HI = "hi"
    const val LANG_DE = "de"

    val LANGUAGE_NAMES = arrayOf(
        "🇬🇧 English",
        "🇮🇳 मराठी (Marathi)",
        "🇮🇳 हिन्दी (Hindi)",
        "🇩🇪 Deutsch (German)"
    )

    val LANGUAGE_CODES = arrayOf(LANG_EN, LANG_MR, LANG_HI, LANG_DE)

    fun getLanguage(context: Context): String {
        return SharedPrefManager.getInstance(context).getLanguage()
    }

    fun applyLocale(context: Context) {
        val langCode = getLanguage(context)
        val appLocale = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun setLocale(context: Context, languageCode: String): Context {
        SharedPrefManager.getInstance(context).saveLanguage(languageCode)
        
        // 1. AndroidX AppCompat application locales
        val appLocale = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)

        // 2. Update context configuration for legacy & immediate in-memory inflation
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val res: Resources = context.resources
        val config: Configuration = Configuration(res.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            context
        }
    }

    fun showLanguageDialog(activity: Activity, onLanguageChanged: ((String) -> Unit)? = null) {
        val currentLang = getLanguage(activity)
        val selectedIdx = when (currentLang) {
            LANG_MR -> 1
            LANG_HI -> 2
            LANG_DE -> 3
            else -> 0
        }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.select_language))
            .setSingleChoiceItems(LANGUAGE_NAMES, selectedIdx) { dialog, which ->
                val chosenCode = LANGUAGE_CODES[which]
                dialog.dismiss()

                if (chosenCode != currentLang) {
                    setLocale(activity, chosenCode)
                    onLanguageChanged?.invoke(chosenCode)

                    val toastMsg = when (chosenCode) {
                        LANG_MR -> "भाषा मराठी मध्ये बदलली आहे 🇮🇳"
                        LANG_HI -> "भाषा हिन्दी में बदल दी गई है 🇮🇳"
                        LANG_DE -> "Sprache auf Deutsch geändert 🇩🇪"
                        else -> "Language set to English 🇬🇧"
                    }
                    Toast.makeText(activity, toastMsg, Toast.LENGTH_SHORT).show()
                    activity.recreate()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
