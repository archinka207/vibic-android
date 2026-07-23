package com.example.vibic

import android.util.Log
import com.example.vibic.network.ApiClient
import com.example.vibic.network.SendCodeRequest
import com.example.vibic.network.VerifyCodeRequest
import com.example.vibic.network.VerifyCodeResponse
import com.example.vibic.network.Verify2FARequest
import com.example.vibic.network.Setup2FARequest
import com.example.vibic.network.TokenManager

class AuthService private constructor() {

    private val api = ApiClient.apiService

    companion object {
        val shared = AuthService()
    }

    suspend fun sendCode(phone: String): Result<Unit> {
        Log.d("AuthService", "📱 Отправка кода на номер: $phone")
        return try {
            val response = api.sendCode(SendCodeRequest(phone))
            if (response.isSuccessful) {
                Log.d("AuthService", "✅ Код успешно отправлен")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Ошибка сервера: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyCode(phone: String, code: String): Result<VerifyCodeResponse> {
        Log.d("AuthService", "🔐 Проверка кода: $code для номера: $phone")
        return try {
            val response = api.verifyCode(VerifyCodeRequest(phone, code))
            val body = response.body()

            if (response.isSuccessful && body != null) {
                Log.d("AuthService", "📲 Статус верификации: ${body.status}")
                Log.d("AuthService", "📲 userId в ответе: ${body.userId}")

                when (body.status) {
                    "success" -> {
                        body.userId?.let { TokenManager.saveUserId(it) }
                        body.accessToken?.let { accessToken ->
                            body.refreshToken?.let { refreshToken ->
                                TokenManager.saveTokens(accessToken, refreshToken)
                                Log.d("AuthService", "✅ Токен сохранен")

                                // ИСПРАВЛЕНИЕ: Отправляем отложенный FCM токен на сервер
                                PushTokenSyncManager.syncPendingToken()
                            }
                        }

                        TokenManager.savePhone(phone)
                        Log.d("AuthService", "✅ Токены и данные сохранены")

                        val userId = TokenManager.getUserId()
                        Log.d("AuthService", "✅ UserId после входа: $userId")
                    }
                    "2fa_required" -> {
                        body.tempToken?.let { TokenManager.saveTempToken(it) }
                        Log.d("AuthService", "🔒 Требуется 2FA, временный токен сохранен")
                    }
                }
                Result.success(body)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("AuthService", "❌ Ошибка ответа: $errorBody")
                Result.failure(Exception("Неверный код или ошибка сервера"))
            }
        } catch (e: Exception) {
            Log.e("AuthService", "❌ Исключение: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun verify2FA(password: String): Result<VerifyCodeResponse> {
        val tempToken = TokenManager.getTempToken()
            ?: return Result.failure(Exception("Нет временного токена"))

        Log.d("AuthService", "🔑 Проверка 2FA пароля")

        return try {
            val response = api.verify2FA(Verify2FARequest(tempToken, password))
            val body = response.body()

            if (response.isSuccessful && body != null) {
                body.userId?.let { TokenManager.saveUserId(it) }
                body.accessToken?.let { accessToken ->
                    body.refreshToken?.let { refreshToken ->
                        TokenManager.saveTokens(accessToken, refreshToken)

                        // ИСПРАВЛЕНИЕ: Отправляем отложенный FCM токен на сервер
                        PushTokenSyncManager.syncPendingToken()
                    }
                }

                Log.d("AuthService", "✅ Токены сохранены после 2FA")
                Result.success(body)
            } else {
                Result.failure(Exception("Неверный облачный пароль"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setup2FA(password: String, backupEmail: String? = null): Result<Unit> {
        Log.d("AuthService", "🔐 Настройка 2FA")
        return try {
            val response = api.setup2FA(Setup2FARequest(password, backupEmail))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Ошибка настройки 2FA"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    val isAuthenticated: Boolean
        get() = TokenManager.getAccessToken() != null

    fun getAccessToken(): String? = TokenManager.getAccessToken()

    fun getRefreshToken(): String? = TokenManager.getRefreshToken()

    fun forceLogout() {
        Log.d("AuthService", "⚠️ Принудительный выход из аккаунта")
        TokenManager.clearTokens()
    }
}