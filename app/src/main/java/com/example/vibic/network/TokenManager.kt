package com.example.vibic.network

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.vibic.MainActivity
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
        Log.d("TokenManager", "saveTokens: accessToken=${accessToken.take(15)}..., refreshToken=${refreshToken.take(15)}...")
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
        prefs.edit().putString(KEY_TEMP_TOKEN, tempToken).apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getTempToken(): String? = prefs.getString(KEY_TEMP_TOKEN, null)

    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun savePhone(phone: String) {
        prefs.edit().putString(KEY_PHONE, phone).apply()
    }

    fun getPhone(): String? = prefs.getString(KEY_PHONE, null)

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
            Log.e("TokenManager", "Нет refresh токена. Форсируем выход из аккаунта.")
            isRefreshing = false
            forceLogout()
            return false
        }

        return try {
            val response = ApiClient.apiService.refresh(RefreshRequest(refreshToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.accessToken != null && body.refreshToken != null) {
                    body.userId?.let { saveUserId(it) }
                    saveTokens(body.accessToken, body.refreshToken)
                    Log.d("TokenManager", "✅ Токен успешно обновлен!")
                    true
                } else {
                    Log.e("TokenManager", "Пустой ответ при обновлении токена. Форсируем выход.")
                    forceLogout()
                    false
                }
            } else {
                Log.e("TokenManager", "❌ Ошибка обновления токена: ${response.code()}")
                if (response.code() == 401) {
                    Log.e("TokenManager", "Refresh Token протух. Форсируем выход из аккаунта.")
                    forceLogout()
                }
                false
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Исключение при обновлении токена", e)
            false
        } finally {
            isRefreshing = false
        }
    }

    // ИСПРАВЛЕНИЕ: Глобальный принудительный выход при мертвой сессии
    private fun forceLogout() {
        clearTokens()

        try {
            WebSocketManager.shared.disconnect()
        } catch (e: Exception) {
            Log.e("TokenManager", "Ошибка отключения сокетов при выходе", e)
        }

        val context = VibicApp.getAppContext()
        val intent = Intent(context, MainActivity::class.java).apply {
            // Очищаем историю экранов и запускаем окно логина как новое задание
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
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