package io.businesscare.app.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.businesscare.app.R 
import io.businesscare.app.data.local.TokenManager
import io.businesscare.app.data.model.LoginRequest
import io.businesscare.app.data.model.LoginResponse
import io.businesscare.app.data.network.ApiClient
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
    private val app = application 

    private val _loginResult = MutableLiveData<LoginResult>(LoginResult.Idle)
    val loginResult: LiveData<LoginResult> = _loginResult

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginResult.value = LoginResult.Error(app.getString(R.string.login_error_email_password_required))
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
                _loginResult.postValue(LoginResult.Error(app.getString(R.string.error_http_code_message, e.code(), errorBody ?: e.message())))
            } catch (e: IOException) {
                _loginResult.postValue(LoginResult.Error(app.getString(R.string.error_network_check_connection_url)))
            } catch (e: Exception) {
                _loginResult.postValue(LoginResult.Error(app.getString(R.string.error_unknown_with_details, e.message ?: app.getString(R.string.error_unknown_default_fallback))))
            }
        }
    }

    fun resetLoginResult() {
        _loginResult.value = LoginResult.Idle
    }
}