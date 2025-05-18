package io.businesscare.app.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import io.businesscare.app.R
import io.businesscare.app.data.local.TokenManager
import io.businesscare.app.databinding.ActivitySettingsBinding
import io.businesscare.app.databinding.DialogLanguageSelectionBinding
import io.businesscare.app.ui.login.LoginActivity
import io.businesscare.app.util.AppConstants
import java.util.Locale


class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var settingsPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsPrefs = getSharedPreferences(AppConstants.PREFS_NAME_SETTINGS, Context.MODE_PRIVATE)
        val languageCode = settingsPrefs.getString(AppConstants.KEY_LANGUAGE, null)
        languageCode?.let { applyAppLanguage(it, false) }


        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(applicationContext)

        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadSettings()
        setupClickListeners()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadSettings() {
        val isDarkMode = settingsPrefs.getBoolean(AppConstants.KEY_THEME_MODE, false)
        binding.switchTheme.isChecked = isDarkMode

        val currentLanguageCode = settingsPrefs.getString(AppConstants.KEY_LANGUAGE, getDefaultLanguage()) ?: getDefaultLanguage()
        binding.textCurrentLanguage.text = getLanguageDisplayString(currentLanguageCode)

        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.textVersion.text = getString(R.string.app_version_format, versionName)
        } catch (e: Exception) {
            binding.textVersion.text = getString(R.string.app_version_format, getString(R.string.not_available_short))
        }
    }

    private fun getDefaultLanguage(): String {
        val systemLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
        return when (systemLocale.language) {
            "en", "es", "de" -> systemLocale.language
            else -> "fr"
        }
    }
    private fun getLanguageDisplayString(languageCode: String): String {
        return when (languageCode) {
            "en" -> getString(R.string.language_english)
            "es" -> getString(R.string.language_spanish)
            "de" -> getString(R.string.language_german)
            "fr" -> getString(R.string.language_french)
            else -> getString(R.string.language_french)
        }
    }


    private fun setupClickListeners() {
        binding.layoutThemeToggle.setOnClickListener {
            binding.switchTheme.toggle()
        }

        binding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            setThemeMode(isChecked)
        }

        binding.layoutLanguage.setOnClickListener {
            showLanguageSelectionDialog()
        }

        binding.layoutAbout.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }

        binding.layoutLogout.setOnClickListener {
            performLogout()
        }
    }

    private fun setThemeMode(isDarkMode: Boolean) {
        settingsPrefs.edit().putBoolean(AppConstants.KEY_THEME_MODE, isDarkMode).apply()

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun showLanguageSelectionDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogLanguageSelectionBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val currentLanguageCode = settingsPrefs.getString(AppConstants.KEY_LANGUAGE, getDefaultLanguage()) ?: getDefaultLanguage()
        when (currentLanguageCode) {
            "en" -> dialogBinding.radioEnglish.isChecked = true
            "es" -> dialogBinding.radioSpanish.isChecked = true
            "de" -> dialogBinding.radioGerman.isChecked = true
            else -> dialogBinding.radioFrench.isChecked = true
        }

        dialogBinding.radioGroupLanguage.setOnCheckedChangeListener { _, checkedId ->
            val selectedLanguageCode = when (checkedId) {
                R.id.radioEnglish -> "en"
                R.id.radioSpanish -> "es"
                R.id.radioGerman -> "de"
                R.id.radioFrench -> "fr"
                else -> getDefaultLanguage()
            }

            if (selectedLanguageCode != currentLanguageCode) {
                applyAppLanguage(selectedLanguageCode, true)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun applyAppLanguage(languageCode: String, shouldRecreate: Boolean) {
        settingsPrefs.edit().putString(AppConstants.KEY_LANGUAGE, languageCode).apply()

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        @Suppress("DEPRECATION")
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)

        if (shouldRecreate) {
            recreate()
        }
    }


    private fun performLogout() {
        tokenManager.clear()

        val loginPrefs = getSharedPreferences(AppConstants.PREFS_NAME_LOGIN, Context.MODE_PRIVATE)
        loginPrefs.edit().clear().apply()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }
}