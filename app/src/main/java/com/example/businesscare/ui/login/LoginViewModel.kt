package com.example.businesscare.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.businesscare.data.local.TokenManager
import com.example.businesscare.data.model.LoginRequest
import com.example.businesscare.data.model.LoginResponse
import com.example.businesscare.data.network.ApiClient
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed class LoginResult {
    object Idle : LoginResult()
    object Loading : LoginResult()
    data class Success(val role: String, val accessToken: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = ApiClient.create(application)
    private val tokenManager = TokenManager(application)

    private val _loginResult = MutableLiveData<LoginResult>(LoginResult.Idle)
    val loginResult: LiveData<LoginResult> = _loginResult

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginResult.value = LoginResult.Error("Email et mot de passe requis.")
            return
        }

        _loginResult.value = LoginResult.Loading

        viewModelScope.launch {
            try {
                val request = LoginRequest(email, password)
                val response: LoginResponse = apiService.login(request)
                tokenManager.saveToken(response.accessToken)
                tokenManager.saveUserRole(response.role)
                _loginResult.postValue(LoginResult.Success(response.role, response.accessToken))
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                _loginResult.postValue(LoginResult.Error("Erreur ${e.code()}: ${errorBody ?: e.message()}"))
            } catch (e: IOException) {
                _loginResult.postValue(LoginResult.Error("Erreur réseau: Vérifiez la connexion et l'URL du serveur."))
            } catch (e: Exception) {
                _loginResult.postValue(LoginResult.Error("Erreur inconnue: ${e.message ?: "Une erreur est survenue"}"))
            }
        }
    }

    fun resetLoginResult() {
        _loginResult.value = LoginResult.Idle
    }
}