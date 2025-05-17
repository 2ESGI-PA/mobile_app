package io.businesscare.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

private const val PREFS_NAME_SETTINGS = "SettingsPrefs"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_LANGUAGE = "language"

class BusinessCareApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()

        initializeTheme()

        initializeLanguage()
    }
    
    private fun initializeTheme() {
        val settingsPrefs = getSharedPreferences(PREFS_NAME_SETTINGS, Context.MODE_PRIVATE)
        val isDarkMode = settingsPrefs.getBoolean(KEY_THEME_MODE, false)
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun initializeLanguage() {
        val settingsPrefs = getSharedPreferences(PREFS_NAME_SETTINGS, Context.MODE_PRIVATE)
        val languageCode = settingsPrefs.getString(KEY_LANGUAGE, "fr") ?: "fr"
        
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration()
        config.locale = locale
        
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}