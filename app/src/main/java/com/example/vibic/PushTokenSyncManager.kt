package com.example.vibic

import android.content.Context
import android.util.Log
import com.example.vibic.network.ApiClient
import com.example.vibic.network.FcmTokenRequest
import com.example.vibic.network.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PushTokenSyncManager {

    private const val TAG = "PushTokenSync"
    private const val PREFS_NAME = "fcm_prefs"
    private const val KEY_PENDING_TOKEN = "pending_fcm_token"

    private val prefs by lazy {
        VibicApp.getAppContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun syncCurrentToken(onComplete: () -> Unit = {}) {
        if (!TokenManager.isAuthenticated) {
            Log.d(TAG, "Пользователь не авторизован, запрос токена отложен")
            onComplete()
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "FCM токен получен из Firebase")
                sendTokenToBackend(token, onComplete)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Не удалось получить FCM токен", e)
                onComplete()
            }
    }

    private fun savePendingToken(token: String) {
        prefs.edit().putString(KEY_PENDING_TOKEN, token).apply()
        Log.d(TAG, "FCM токен сохранен локально")
    }

    private fun getPendingToken(): String? {
        return prefs.getString(KEY_PENDING_TOKEN, null)
    }

    private fun clearPendingToken() {
        prefs.edit().remove(KEY_PENDING_TOKEN).apply()
    }

    fun syncPendingToken() {
        val token = getPendingToken()

        if (token != null) {
            Log.d(TAG, "Обнаружен отложенный FCM токен, отправляем на сервер")
            sendTokenToBackend(token)
        } else {
            Log.d(TAG, "Нет отложенного токена, запрашиваем актуальный из Firebase")
            syncCurrentToken()
        }
    }

    fun sendTokenToBackend(token: String, onComplete: () -> Unit = {}) {
        if (!TokenManager.isAuthenticated) {
            Log.d(TAG, "Нет access token, FCM токен отложен")
            savePendingToken(token)
            onComplete()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.apiService.registerFcmToken(
                    FcmTokenRequest(token = token)
                )

                if (response.isSuccessful) {
                    Log.d(TAG, "FCM токен успешно зарегистрирован на backend")
                    clearPendingToken()
                } else {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    Log.e(TAG, "Ошибка регистрации токена: ${response.code()}, body=$errorBody")
                    savePendingToken(token)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при отправке токена на backend", e)
                savePendingToken(token)
            } finally {
                onComplete()
            }
        }
    }

    fun deleteCurrentTokenFromBackend() {
        Log.d(TAG, "Удаление FCM токена на backend пропущено: endpoint deleteFcmToken отсутствует")
        clearPendingToken()
    }
}