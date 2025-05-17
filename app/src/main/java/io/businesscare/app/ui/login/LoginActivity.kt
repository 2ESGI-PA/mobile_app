package io.businesscare.app.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import io.businesscare.app.R
import io.businesscare.app.databinding.ActivityLoginBinding 
import io.businesscare.app.nfc.MainActivity as NfcActivity 
import io.businesscare.app.ui.schedule.ScheduleActivity
import io.businesscare.app.util.AppConstants

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val loginViewModel: LoginViewModel by viewModels()
    private lateinit var loginSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loginSharedPreferences = getSharedPreferences(AppConstants.PREFS_NAME_LOGIN, Context.MODE_PRIVATE)

        setupPreFill()
        setupObservers()
        setupListeners()
    }

    private fun setupPreFill() {
        val rememberMe = loginSharedPreferences.getBoolean(AppConstants.KEY_REMEMBER_ME, false)
        binding.checkboxRememberMe.isChecked = rememberMe 

        val prefillEmail = intent.getStringExtra(AppConstants.EXTRA_PREFILL_EMAIL)
        if (prefillEmail != null) {
            binding.editTextEmail.setText(prefillEmail) 
        } else if (rememberMe) {
            binding.editTextEmail.setText(loginSharedPreferences.getString(AppConstants.KEY_LOGIN_EMAIL, "")) 
        }
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupListeners() {
        binding.buttonLogin.setOnClickListener { 
            val email = binding.editTextEmail.text.toString().trim() 
            val password = binding.editTextPassword.text.toString().trim() 
            loginViewModel.login(email, password)
        }
    }

    private fun handleLoginSuccess(role: String) {
        val editor = loginSharedPreferences.edit()
        val currentEmail = binding.editTextEmail.text.toString().trim() 

        if (binding.checkboxRememberMe.isChecked) { 
            editor.putString(AppConstants.KEY_LOGIN_EMAIL, currentEmail)
            editor.putBoolean(AppConstants.KEY_REMEMBER_ME, true)
            editor.putString(AppConstants.KEY_USER_ROLE_FROM_LOGIN_PREFS, role)
        } else {
            editor.remove(AppConstants.KEY_LOGIN_EMAIL)
            editor.putBoolean(AppConstants.KEY_REMEMBER_ME, false)
            editor.remove(AppConstants.KEY_USER_ROLE_FROM_LOGIN_PREFS)
        }
        editor.apply()

        when (role.lowercase()) {
            "admin" -> navigateToActivity(NfcActivity::class.java)
            "client" -> navigateToActivity(ScheduleActivity::class.java)
            else -> {
                Toast.makeText(this, getString(R.string.unknown_user_role_error, role), Toast.LENGTH_LONG).show()
                loginViewModel.resetLoginResult()
            }
        }
    }

    private fun setupObservers() {
        loginViewModel.loginResult.observe(this) { result ->
            val isLoading = result is LoginResult.Loading
            binding.progressBarLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.buttonLogin.isEnabled = !isLoading 
            binding.editTextEmail.isEnabled = !isLoading 
            binding.editTextPassword.isEnabled = !isLoading 
            binding.checkboxRememberMe.isEnabled = !isLoading 

            when (result) {
                is LoginResult.Success -> {
                    Toast.makeText(this, getString(R.string.login_success_message), Toast.LENGTH_SHORT).show()
                    handleLoginSuccess(result.role)
                }
                is LoginResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
                is LoginResult.Idle -> {  }
                is LoginResult.Loading -> {}
            }
        }
    }
}