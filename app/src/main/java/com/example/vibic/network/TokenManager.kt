package com.example.vibic.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.vibic.VibicApp
import kotlinx.coroutines.delay
import org.json.JSONObject

object TokenManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TEMP_TOKEN = "temp_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_PHONE = "phone"

    private val prefs: SharedPreferences by lazy {
        VibicApp.getInstance().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var isRefreshing = false

    fun saveTokens(accessToken: String, refreshToken: String) {
        Log.d("TokenManager", "saveTokens: accessToken=${accessToken.take(50)}..., refreshToken=${refreshToken.take(20)}...")
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            remove(KEY_TEMP_TOKEN)
        }.apply()

        val currentUserId = getUserId()
        if (currentUserId.isNullOrEmpty() || currentUserId.startsWith("+") || currentUserId.all { it.isDigit() }) {
            extractUserIdFromToken(accessToken)
        }
        Log.d("TokenManager", "Токены сохранены, userId: ${getUserId()}")
    }

    fun saveTempToken(tempToken: String) {
        Log.d("TokenManager", "saveTempToken: ${tempToken.take(50)}...")
        prefs.edit().putString(KEY_TEMP_TOKEN, tempToken).apply()
    }

    fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        Log.d("TokenManager", "getAccessToken: ${if (token != null) "есть токен (${token.take(20)}...)" else "нет токена"}")
        return token
    }

    fun getRefreshToken(): String? {
        val token = prefs.getString(KEY_REFRESH_TOKEN, null)
        Log.d("TokenManager", "getRefreshToken: ${if (token != null) "есть токен (${token.take(20)}...)" else "нет токена"}")
        return token
    }

    fun getTempToken(): String? {
        val token = prefs.getString(KEY_TEMP_TOKEN, null)
        Log.d("TokenManager", "getTempToken: ${if (token != null) "есть токен (${token.take(20)}...)" else "нет токена"}")
        return token
    }

    fun saveUserId(userId: String) {
        Log.d("TokenManager", "saveUserId: $userId")
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): String? {
        val userId = prefs.getString(KEY_USER_ID, null)
        Log.d("TokenManager", "getUserId: $userId")
        return userId
    }

    fun savePhone(phone: String) {
        Log.d("TokenManager", "savePhone: $phone")
        prefs.edit().putString(KEY_PHONE, phone).apply()
    }

    fun getPhone(): String? {
        val phone = prefs.getString(KEY_PHONE, null)
        Log.d("TokenManager", "getPhone: $phone")
        return phone
    }

    fun clearTokens() {
        Log.d("TokenManager", "clearTokens - очистка всех данных")
        prefs.edit().clear().apply()
        isRefreshing = false
    }

    val isAuthenticated: Boolean
        get() = getAccessToken() != null

    fun isTokenExpired(): Boolean {
        val token = getAccessToken() ?: return true
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
                val json = JSONObject(payload)
                val exp = json.optLong("exp", 0)
                val currentTime = System.currentTimeMillis() / 1000
                exp - currentTime < 300
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Ошибка проверки токена", e)
            true
        }
    }

    suspend fun refreshTokenIfNeeded(): Boolean {
        return if (isTokenExpired()) refreshToken() else true
    }

    suspend fun refreshToken(): Boolean {
        if (isRefreshing) {
            Log.d("TokenManager", "Уже выполняется обновление токена, ждем...")
            delay(1000)
            return getAccessToken() != null
        }

        isRefreshing = true
        val refreshToken = getRefreshToken()

        if (refreshToken.isNullOrBlank()) {
            Log.e("TokenManager", "Нет refresh токена")
            isRefreshing = false
            return false
        }

        return try {
            val response = ApiClient.apiService.refresh(RefreshRequest(refreshToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.accessToken != null && body.refreshToken != null) {
                    body.userId?.let { saveUserId(it) }
                    saveTokens(body.accessToken, body.refreshToken)
                    WebSocketManager.shared.forceReconnect()
                    true
                } else {
                    Log.e("TokenManager", "Пустой ответ при обновлении токена")
                    false
                }
            } else {
                Log.e("TokenManager", "Ошибка обновления токена: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Исключение при обновлении токена", e)
            false
        } finally {
            isRefreshing = false
        }
    }

    private fun extractUserIdFromToken(token: String) {
        try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
                val jsonObject = JSONObject(payload)

                val sub = jsonObject.optString("sub")
                val uid = jsonObject.optString("user_id")

                val isSubPhone = sub.startsWith("+") || sub.all { it.isDigit() }

                val finalId = if (isSubPhone && uid.isNotEmpty()) {
                    uid
                } else if (uid.isNotEmpty() && uid.contains("-")) {
                    uid
                } else if (sub.isNotEmpty()) {
                    sub
                } else {
                    uid
                }

                if (finalId.isNotEmpty() && !(finalId.startsWith("+") || finalId.all { it.isDigit() })) {
                    saveUserId(finalId)
                }
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Ошибка при декодировании токена", e)
        }
    }
}