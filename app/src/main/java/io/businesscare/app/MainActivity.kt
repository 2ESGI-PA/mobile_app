package io.businesscare.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.businesscare.app.data.local.TokenManager
import io.businesscare.app.data.model.LoginRequest
import io.businesscare.app.data.network.ApiClient
import io.businesscare.app.nfc.MainActivity as NfcActivity
import io.businesscare.app.ui.login.LoginActivity
import io.businesscare.app.ui.schedule.ScheduleActivity
import kotlinx.coroutines.launch

private const val PREFS_NAME_LOGIN = "LoginPrefs"
private const val KEY_LOGIN_EMAIL = "email"
private const val KEY_LOGIN_PASSWORD = "password"
private const val KEY_REMEMBER_ME = "rememberMe"
private const val KEY_USER_ROLE_FROM_LOGIN_PREFS = "userRole"

class MainActivity : AppCompatActivity() {

    private lateinit var tokenManager: TokenManager
    private lateinit var loginSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(applicationContext)
        loginSharedPreferences = getSharedPreferences(PREFS_NAME_LOGIN, Context.MODE_PRIVATE)
        decideNextActivity()
    }

    private fun decideNextActivity() {
        val token = tokenManager.getToken()
        val roleFromTokenManager = tokenManager.getUserRole()

        if (token != null && !roleFromTokenManager.isNullOrBlank()) {
            navigateToActivityBasedOnRole(roleFromTokenManager)
            return
        }

        val rememberMe = loginSharedPreferences.getBoolean(KEY_REMEMBER_ME, false)
        if (rememberMe) {
            val email = loginSharedPreferences.getString(KEY_LOGIN_EMAIL, null)
            val password = loginSharedPreferences.getString(KEY_LOGIN_PASSWORD, null)
            val roleFromLoginPrefs = loginSharedPreferences.getString(KEY_USER_ROLE_FROM_LOGIN_PREFS, null)


            if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                lifecycleScope.launch {
                    tryToLoginInBackground(email, password, roleFromLoginPrefs)
                }
                return
            } else if (!email.isNullOrBlank() && !roleFromLoginPrefs.isNullOrBlank() && password.isNullOrBlank()){
                navigateToActivityBasedOnRole(roleFromLoginPrefs)
                return
            }
        }
        navigateToLoginActivity()
    }

    private suspend fun tryToLoginInBackground(email: String, pass: String, cachedRole: String?) {
        val apiService = ApiClient.create(application)
        try {
            val response = apiService.login(LoginRequest(email, pass))
            tokenManager.saveToken(response.accessToken)
            tokenManager.saveUserRole(response.role)
            loginSharedPreferences.edit().putString(KEY_USER_ROLE_FROM_LOGIN_PREFS, response.role).apply()
            navigateToActivityBasedOnRole(response.role)
        } catch (e: Exception) {
            Log.e("MainActivity", "Background login failed: ${e.message}")
            if (!cachedRole.isNullOrBlank()) {
                Log.d("MainActivity", "Using cached role '$cachedRole' after background login failure.")
                navigateToActivityBasedOnRole(cachedRole)
            } else {
                clearRememberMeAndGoToLogin()
            }
        }
    }

    private fun clearRememberMeAndGoToLogin() {
        loginSharedPreferences.edit()
            .remove(KEY_LOGIN_EMAIL)
            .remove(KEY_LOGIN_PASSWORD)
            .putBoolean(KEY_REMEMBER_ME, false)
            .remove(KEY_USER_ROLE_FROM_LOGIN_PREFS)
            .apply()
        navigateToLoginActivity()
    }

    private fun navigateToActivityBasedOnRole(role: String) {
        val intent = when (role.lowercase()) {
            "admin" -> Intent(this, NfcActivity::class.java)
            "client" -> Intent(this, ScheduleActivity::class.java)
            else -> {
                Log.w("MainActivity", "Unknown role '$role'. Defaulting to LoginActivity.")
                Intent(this, LoginActivity::class.java)
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}