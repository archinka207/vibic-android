package com.example.vibic

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.vibic.network.TokenManager
import com.example.vibic.network.WebSocketManager
import kotlinx.coroutines.*

class VibicApp : Application() {

    companion object {
        private var instance: VibicApp? = null

        fun getInstance(): VibicApp {
            return instance ?: throw IllegalStateException("VibicApp not initialized")
        }

        fun getAppContext(): Context = getInstance().applicationContext
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d("VibicApp", "=== VibicApp onCreate ===")

        val token = TokenManager.getAccessToken()
        val userId = TokenManager.getUserId()
        Log.d("VibicApp", "Токен: ${token?.take(50)}...")
        Log.d("VibicApp", "UserId: $userId")

        // Регистрируем мониторинг сети
        registerNetworkMonitoring()

        // Фоновый сервис WebSocketService.start() удален!
        // Теперь уведомление висеть не будет.
    }

    private fun registerNetworkMonitoring() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("VibicApp", "🌐 Сеть доступна: $network")
                // При появлении сети пробуем восстановить WebSocket-соединение
                if (TokenManager.getAccessToken() != null) {
                    scope.launch {
                        delay(1000)
                        WebSocketManager.shared.connect()
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d("VibicApp", "⚠️ Сеть потеряна: $network")
            }
        }

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            Log.e("VibicApp", "Ошибка регистрации сетевого мониторинга", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        networkCallback?.let {
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e("VibicApp", "Ошибка отмены сетевого мониторинга", e)
            }
        }
        scope.cancel()
    }
}