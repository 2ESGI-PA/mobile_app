package com.example.businesscare.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.businesscare.R
import com.example.businesscare.data.local.TokenManager
import com.example.businesscare.databinding.ActivitySettingsBinding
import com.example.businesscare.databinding.DialogLanguageSelectionBinding
import com.example.businesscare.ui.login.LoginActivity
import java.util.Locale


private const val PREFS_NAME_LOGIN = "LoginPrefs"
private const val PREFS_NAME_SETTINGS = "SettingsPrefs"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_LANGUAGE = "language"

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var settingsPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        settingsPrefs = getSharedPreferences(PREFS_NAME_SETTINGS, Context.MODE_PRIVATE)


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

        val isDarkMode = settingsPrefs.getBoolean(KEY_THEME_MODE, false)
        binding.switchTheme.isChecked = isDarkMode


        val currentLanguageCode = settingsPrefs.getString(KEY_LANGUAGE, "fr") ?: "fr"
        binding.textCurrentLanguage.text = when (currentLanguageCode) {
            "en" -> getString(R.string.language_english)
            "es" -> getString(R.string.language_spanish)
            "de" -> getString(R.string.language_german)
            else -> getString(R.string.language_french)
        }

        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.textVersion.text = getString(R.string.app_version_format, versionName)
        } catch (e: Exception) {
            binding.textVersion.text = getString(R.string.app_version_format, "N/A")
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

        settingsPrefs.edit().putBoolean(KEY_THEME_MODE, isDarkMode).apply()


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


        val currentLanguageCode = settingsPrefs.getString(KEY_LANGUAGE, "fr") ?: "fr"
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
                else -> "fr"
            }

            if (selectedLanguageCode != currentLanguageCode) {
                setAppLanguage(selectedLanguageCode)
                dialog.dismiss()

                val intent = intent
                finish()
                startActivity(intent)

                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            } else {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun setAppLanguage(languageCode: String) {

        settingsPrefs.edit().putString(KEY_LANGUAGE, languageCode).apply()


        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        resources.updateConfiguration(config, resources.displayMetrics)

    }

    private fun performLogout() {
        tokenManager.clear()

        val loginPrefs = getSharedPreferences(PREFS_NAME_LOGIN, Context.MODE_PRIVATE)
        loginPrefs.edit().clear().apply()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }

}