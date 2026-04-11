package com.example.vibic

import android.content.Context
import com.example.vibic.network.TokenManager
import com.example.vibic.network.RegisterResponse  // Добавить этот импорт

class AuthRepository(private val context: Context) {

    companion object {
        private const val PREFS_PHONE = "phone_prefs"
        private const val KEY_PHONE = "phone_number"
    }

    init {
        TokenManager
    }

    var authToken: String?
        get() = TokenManager.getAccessToken()
        set(value) {
            value?.let {
                TokenManager.saveTokens(it, TokenManager.getRefreshToken() ?: "")
            }
        }

    var phoneNumber: String?
        get() = context.getSharedPreferences(PREFS_PHONE, Context.MODE_PRIVATE)
            .getString(KEY_PHONE, null)
        set(value) {
            context.getSharedPreferences(PREFS_PHONE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PHONE, value)
                .apply()
        }

    val isAuthenticated: Boolean
        get() = TokenManager.getAccessToken() != null

    fun saveToken(token: String) {
        TokenManager.saveTokens(token, TokenManager.getRefreshToken() ?: "")
    }

    fun savePhoneNumber(phone: String) {
        phoneNumber = phone
    }

    fun clear() {
        TokenManager.clearTokens()
        context.getSharedPreferences(PREFS_PHONE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    suspend fun register(phone: String, name: String): Result<RegisterResponse> {
        // TODO: Implement registration
        return Result.failure(Exception("Not implemented"))
    }
}