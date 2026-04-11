package com.example.vibic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vibic.databinding.ActivityMainAppBinding
import com.example.vibic.network.ApiClient
import com.example.vibic.network.FcmTokenRequest
import com.example.vibic.network.WebSocketManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivityApp : AppCompatActivity() {

    private lateinit var binding: ActivityMainAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Нормальный edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Цвета системных баров
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        // Фоны
        binding.mainLayout.setBackgroundResource(0)
        binding.mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
        binding.contentFrame.setBackgroundResource(R.drawable.bg_gradient)

        // Запрос разрешения на уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            } else {
                getFcmToken()
            }
        } else {
            getFcmToken()
        }

        if (savedInstanceState == null) {
            showFragment(MainFragment())
        }
    }

    // --- УПРАВЛЕНИЕ ЖИЗНЕННЫМ ЦИКЛОМ WEBSOCKET ---

    override fun onResume() {
        super.onResume()
        Log.d("MainActivityApp", "✅ Приложение на экране - подключаем WebSocket")
        // Подключаем сокеты, когда приложение развернуто
        WebSocketManager.shared.connect()
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivityApp", "💤 Приложение свернуто - отключаем WebSocket")
        // Отключаем сокеты, когда приложение свернуто,
        // чтобы экономить батарею. Уведомления придут через FCM.
        WebSocketManager.shared.disconnect()
    }

    // ----------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivityApp", "✅ Разрешение на уведомления получено")
                getFcmToken()
            } else {
                Log.d("MainActivityApp", "❌ Разрешение на уведомления отклонено")
            }
        }
    }

    private fun getFcmToken() {
        Log.d("MainActivityApp", "🚀 Запрашиваем токен FCM у Google Services...")

        // Показываем на экране, что процесс пошел
        android.widget.Toast.makeText(this, "Запрос токена...", android.widget.Toast.LENGTH_SHORT).show()

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("MainActivityApp", "🔥 FCM токен получен: $token")

                // Показываем на экране, что токен получен от Google
                android.widget.Toast.makeText(this, "Токен от Google ПОЛУЧЕН!", android.widget.Toast.LENGTH_LONG).show()

                lifecycleScope.launch {
                    try {
                        val response = ApiClient.apiService.registerFcmToken(
                            FcmTokenRequest(fcmToken = token, deviceType = "android")
                        )

                        if (response.isSuccessful) {
                            Log.d("MainActivityApp", "✅ Токен успешно отправлен на бэкенд")

                            // Показываем на экране, что токен улетел на твой Go-сервер
                            runOnUiThread {
                                android.widget.Toast.makeText(this@MainActivityApp, "Токен отправлен на сервер!", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
                            runOnUiThread {
                                android.widget.Toast.makeText(this@MainActivityApp, "Ошибка сервера: ${response.code()}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            android.widget.Toast.makeText(this@MainActivityApp, "Нет связи с сервером", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                android.widget.Toast.makeText(this, "Ошибка Google: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, fragment)
            .commit()
    }
}