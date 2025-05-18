package io.businesscare.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import io.businesscare.app.MainActivity
import io.businesscare.app.R
import io.businesscare.app.data.model.LoginRequest
import io.businesscare.app.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.email_password_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginViewModel.loginUser(LoginRequest(email, password))
        }
    }

    private fun setupObservers() {
        loginViewModel.loginStatus.observe(this) { status ->
            binding.progressBarLogin.visibility = if (status == LoginStatus.LOADING) View.VISIBLE else View.GONE
            binding.buttonLogin.isEnabled = status != LoginStatus.LOADING
        }

        loginViewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                loginViewModel.resetLoginStatus()
            }
        }

        loginViewModel.navigateToRoleScreen.observe(this) { role ->
            role?.let {
                Toast.makeText(this, getString(R.string.login_successful_role, it), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                loginViewModel.resetNavigation()
            }
        }
    }
}