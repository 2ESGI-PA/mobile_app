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

enum class LoginStatus { IDLE, LOADING, SUCCESS, ERROR }

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = ApiClient.create(application)
    private val tokenManager = TokenManager(application)

    private val _loginStatus = MutableLiveData<LoginStatus>(LoginStatus.IDLE)
    val loginStatus: LiveData<LoginStatus> = _loginStatus

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _navigateToRoleScreen = MutableLiveData<String?>()
    val navigateToRoleScreen: LiveData<String?> = _navigateToRoleScreen

    fun loginUser(loginRequest: LoginRequest) {
        _loginStatus.value = LoginStatus.LOADING
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val response: LoginResponse = apiService.login(loginRequest)
                tokenManager.saveToken(response.accessToken)
                tokenManager.saveUserRole(response.role)
                tokenManager.saveUserId(response.userId)
                _loginStatus.postValue(LoginStatus.SUCCESS)
                _navigateToRoleScreen.postValue(response.role)
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                var specificMessage = getApplication<Application>().getString(R.string.login_failed_generic)
                if (e.code() == 401) {
                    specificMessage = getApplication<Application>().getString(R.string.login_failed_unauthorized)
                }
                _errorMessage.postValue(specificMessage + (if (!errorBody.isNullOrBlank()) "\n${errorBody}" else ""))
                _loginStatus.postValue(LoginStatus.ERROR)
            } catch (e: IOException) {
                _errorMessage.postValue(getApplication<Application>().getString(R.string.network_error_login))
                _loginStatus.postValue(LoginStatus.ERROR)
            } catch (e: Exception) {
                _errorMessage.postValue(getApplication<Application>().getString(R.string.unknown_error_login, e.message ?: "N/A"))
                _loginStatus.postValue(LoginStatus.ERROR)
            }
        }
    }

    fun resetNavigation() {
        _navigateToRoleScreen.value = null
    }

    fun resetLoginStatus() {
        _loginStatus.value = LoginStatus.IDLE
        _errorMessage.value = null
    }
}