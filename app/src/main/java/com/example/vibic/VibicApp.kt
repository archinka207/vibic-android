package com.example.vibic

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.vibic.network.TokenManager
import com.example.vibic.network.WebSocketManager
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VibicApp : Application() {

    companion object {
        private const val TAG = "VibicApp"
        private var instance: VibicApp? = null

        fun getInstance(): VibicApp {
            return instance ?: throw IllegalStateException("VibicApp not initialized")
        }

        fun getAppContext(): Context = getInstance().applicationContext
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Текущая видимая Activity — нужна, чтобы показывать оверлей с котом поверх неё
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        applySavedThemeMode()

        // Холодный старт — всегда открываем Чаты. onCreate() Application
        // вызывается только тут, а не при пересоздании Activity, поэтому
        // внутри сессии (свайп назад, смена темы) last_tab не трогается
        // и MainFragment восстанавливает актуальную вкладку сам.
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putString("last_tab", "chats").apply()

        FirebaseApp.initializeApp(this)
        Log.d(TAG, "=== VibicApp onCreate ===")

        val token = TokenManager.getAccessToken()
        val userId = TokenManager.getUserId()
        Log.d(TAG, "Токен: ${token?.take(50)}...")
        Log.d(TAG, "UserId: $userId")

        registerNetworkMonitoring()
        registerLifecycleMonitoring()
        registerActivityTracking()
        observeErrors()
    }

    /** Применяет тему, сохранённую пользователем на экране выбора темы. */
    private fun applySavedThemeMode() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "system")
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    /**
     * Следим за тем, какая Activity сейчас на экране, чтобы знать,
     * поверх чего рисовать оверлей с котом при ошибке.
     */
    private fun registerActivityTracking() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) currentActivity = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Слушаем глобальную шину ошибок. Когда сетевой слой репортит ошибку
     * (400/404/500/нет сети и т.д.), показываем кота поверх текущего экрана.
     */
    private fun observeErrors() {
        CoroutineScope(Dispatchers.Main).launch {
            ErrorBus.errors.collect { error ->
                currentActivity?.let { activity ->
                    CatErrorOverlay.show(activity, error)
                }
            }
        }
    }

    /**
     * Слушаем переходы всего процесса foreground ↔ background.
     * ON_START — пользователь открыл приложение (или переключился обратно из другого).
     * ON_STOP  — приложение ушло в фон (все Activity не видны).
     */
    private fun registerLifecycleMonitoring() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Log.d(TAG, "▶️ App foreground")
                if (TokenManager.getAccessToken() != null) {
                    // forceReconnect: рвём старый сокет (если он мог зависнуть, пока спали)
                    // и подключаемся заново — это даёт быстрый "живой" WS при возврате в чат.
                    WebSocketManager.shared.forceReconnect()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                Log.d(TAG, "⏸ App background")
                // Отключаемся — пока приложение в фоне, держать WS смысла нет
                // (для пушей Firebase используется FCM, который работает независимо).
                WebSocketManager.shared.disconnect()
            }
        })
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
                Log.d(TAG, "🌐 Сеть доступна: $network")
                // Уведомляем менеджер, он сам решит — подключаться или нет
                // (только если есть токен и приложение в активном состоянии)
                if (TokenManager.getAccessToken() != null) {
                    WebSocketManager.shared.onNetworkAvailable()
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "⚠️ Сеть потеряна: $network")
                WebSocketManager.shared.onNetworkLost()
            }
        }

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка регистрации сетевого мониторинга", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        networkCallback?.let {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отмены сетевого мониторинга", e)
            }
        }
    }
}