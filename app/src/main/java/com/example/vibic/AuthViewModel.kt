package com.example.vibic

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.vibic.network.ApiClient
import com.example.vibic.network.SendCodeRequest
import com.example.vibic.network.VerifyCodeRequest
import com.example.vibic.network.VerifyCodeResponse
import com.example.vibic.network.TokenManager
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = ApiClient.apiService
    private val repository = AuthRepository(application.applicationContext)

    init {
        TokenManager  // TokenManager инициализируется в своем companion object
    }

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    sealed class AuthState {
        object Idle : AuthState()
        data class Success(val response: VerifyCodeResponse?) : AuthState()
        data class TwoFARequired(val tempToken: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    fun sendCode(phoneNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Idle

            try {
                val response = apiService.sendCode(SendCodeRequest(phoneNumber))
                if (response.isSuccessful) {
                    repository.savePhoneNumber(phoneNumber)
                    _authState.value = AuthState.Success(null)
                } else {
                    val errorMsg = when (response.code()) {
                        400 -> "Неверный формат номера телефона"
                        429 -> "Слишком много попыток. Попробуйте позже"
                        else -> "Ошибка отправки кода: ${response.code()}"
                    }
                    _authState.value = AuthState.Error(errorMsg)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "sendCode error", e)
                val errorMsg = when (e) {
                    is java.net.ConnectException -> "Нет подключения к интернету"
                    is java.net.SocketTimeoutException -> "Превышено время ожидания"
                    else -> e.message ?: "Неизвестная ошибка"
                }
                _authState.value = AuthState.Error(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun verifyCode(phoneNumber: String, code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Idle

            try {
                val response = apiService.verifyCode(VerifyCodeRequest(phoneNumber, code))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        when (body.status) {
                            "success" -> {
                                body.accessToken?.let { token ->
                                    repository.saveToken(token)
                                }
                                _authState.value = AuthState.Success(body)
                            }
                            "2fa_required" -> {
                                body.tempToken?.let { tempToken ->
                                    TokenManager.saveTempToken(tempToken)
                                    _authState.value = AuthState.TwoFARequired(tempToken)
                                } ?: run {
                                    _authState.value = AuthState.Error("Ошибка: нет временного токена")
                                }
                            }
                            else -> {
                                _authState.value = AuthState.Error("Неизвестный статус ответа")
                            }
                        }
                    } else {
                        _authState.value = AuthState.Error("Пустой ответ от сервера")
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        400 -> "Неверный код подтверждения"
                        404 -> "Код не найден или истек"
                        429 -> "Слишком много попыток. Попробуйте позже"
                        else -> "Ошибка проверки кода: ${response.code()}"
                    }
                    _authState.value = AuthState.Error(errorMsg)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "verifyCode error", e)
                val errorMsg = when (e) {
                    is java.net.ConnectException -> "Нет подключения к интернету"
                    is java.net.SocketTimeoutException -> "Превышено время ожидания"
                    else -> e.message ?: "Неизвестная ошибка"
                }
                _authState.value = AuthState.Error(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clear()
            _authState.value = AuthState.Idle
        }
    }

    val isAuthenticated: Boolean
        get() = repository.isAuthenticated

    fun getSavedPhoneNumber(): String? = repository.phoneNumber
}