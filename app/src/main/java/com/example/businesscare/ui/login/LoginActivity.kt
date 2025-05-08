package com.example.businesscare.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.businesscare.data.local.TokenManager
import com.example.businesscare.databinding.ActivityLoginBinding
import com.example.businesscare.nfc.MainActivity as NfcMainActivity
import com.example.businesscare.ui.schedule.ScheduleActivity

private const val PREFS_NAME_LOGIN = "LoginPrefs"
private const val KEY_LOGIN_EMAIL = "email"
private const val KEY_LOGIN_PASSWORD = "password"
private const val KEY_REMEMBER_ME = "rememberMe"
private const val KEY_USER_ROLE_FROM_LOGIN_PREFS = "userRole"

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val loginViewModel: LoginViewModel by viewModels()
    private lateinit var loginSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loginSharedPreferences = getSharedPreferences(PREFS_NAME_LOGIN, Context.MODE_PRIVATE)

        setupPreFill()
        setupObservers()
        setupListeners()
    }

    private fun setupPreFill() {
        val rememberMe = loginSharedPreferences.getBoolean(KEY_REMEMBER_ME, false)
        binding.cbRememberMe.isChecked = rememberMe
        if (rememberMe) {
            binding.etEmail.setText(loginSharedPreferences.getString(KEY_LOGIN_EMAIL, ""))
            binding.etPassword.setText(loginSharedPreferences.getString(KEY_LOGIN_PASSWORD, ""))
        }
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            loginViewModel.login(email, password)
        }
    }

    private fun handleLoginSuccess(role: String, accessToken: String) {

        val editor = loginSharedPreferences.edit()
        val currentEmail = binding.etEmail.text.toString().trim()
        val currentPassword = binding.etPassword.text.toString().trim()

        if (binding.cbRememberMe.isChecked) {
            editor.putString(KEY_LOGIN_EMAIL, currentEmail)
            editor.putString(KEY_LOGIN_PASSWORD, currentPassword)
            editor.putBoolean(KEY_REMEMBER_ME, true)
            editor.putString(KEY_USER_ROLE_FROM_LOGIN_PREFS, role)
        } else {

            editor.remove(KEY_LOGIN_EMAIL)
            editor.remove(KEY_LOGIN_PASSWORD)
            editor.putBoolean(KEY_REMEMBER_ME, false)
            editor.remove(KEY_USER_ROLE_FROM_LOGIN_PREFS)

        }
        editor.apply()

        when (role.lowercase()) {
            "admin" -> navigateToActivity(NfcMainActivity::class.java)
            "client" -> navigateToActivity(ScheduleActivity::class.java)
            else -> {
                Toast.makeText(this, "Rôle utilisateur inconnu: $role.", Toast.LENGTH_LONG).show()
                loginViewModel.resetLoginResult()
            }
        }
    }

    private fun setupObservers() {
        loginViewModel.loginResult.observe(this) { result ->
            val isLoading = result is LoginResult.Loading
            binding.progressBarLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !isLoading
            binding.etEmail.isEnabled = !isLoading
            binding.etPassword.isEnabled = !isLoading
            binding.cbRememberMe.isEnabled = !isLoading

            when (result) {
                is LoginResult.Success -> {
                    Toast.makeText(this, "Connexion réussie!", Toast.LENGTH_SHORT).show()
                    handleLoginSuccess(result.role, result.accessToken)
                }
                is LoginResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()

                }
                is LoginResult.Idle -> { }
                is LoginResult.Loading -> { }
            }
        }
    }
}