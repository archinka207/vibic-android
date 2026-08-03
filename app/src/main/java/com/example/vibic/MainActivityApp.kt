package com.example.vibic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.example.vibic.databinding.ActivityMainAppBinding
import com.example.vibic.network.WebSocketManager

class MainActivityApp : AppCompatActivity() {

    private lateinit var binding: ActivityMainAppBinding

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("MainActivityApp", "✅ Разрешение на уведомления выдано")
                PushTokenSyncManager.syncCurrentToken()
            } else {
                Log.d("MainActivityApp", "⚠️ Разрешение на уведомления отклонено")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val isLight = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight

        if (savedInstanceState == null) {
            showFragment(MainFragment())
        }

        requestNotificationPermissionIfNeeded()
        openChatFromPushIfNeeded(intent)

        // ИСПРАВЛЕНИЕ: Ждем завершения отправки FCM токена
        PushTokenSyncManager.syncCurrentToken {
            try {
                // Вызываем в UI-потоке, чтобы безопасно запустить сокеты
                runOnUiThread {
                    Log.d("MainActivityApp", "Токен синхронизирован. Подключаем сокет.")
                    WebSocketManager.shared.forceReconnect()
                }
            } catch (e: Exception) {
                Log.e("MainActivityApp", "Ошибка подключения WebSocket", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (!WebSocketManager.shared.isConnectedValue) {
                Log.d("MainActivityApp", "⚡ Приложение развернуто, проверяем подключение WebSocket")
                // ИСПРАВЛЕНИЕ: Используем connect() вместо forceReconnect().
                // Так мы не обрываем только что начавшееся подключение из onCreate.
                WebSocketManager.shared.connect()
            }
        } catch (e: Exception) {
            Log.e("MainActivityApp", "Ошибка старта WebSocket в onResume", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openChatFromPushIfNeeded(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openChatFromPushIfNeeded(intent: Intent?) {
        val chatId = intent?.getStringExtra("chat_id") ?: return
        if (chatId.isBlank()) return

        val chatIntent = Intent(this, ChatDetailActivity::class.java).apply {
            putExtra("chat_id", chatId)
            putExtra("chat_name", "Чат")
        }
        startActivity(chatIntent)
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, fragment)
            .commit()
    }

    // =======================================================
    // МАГИЯ ЗДЕСЬ: Глобальное скрытие клавиатуры по клику вне
    // =======================================================
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)

                // Если клик был НЕ по полю ввода
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

                    // hideSoftInputFromWindow возвращает true, если клавиатура реально была открыта
                    val wasKeyboardOpen = imm.hideSoftInputFromWindow(v.windowToken, 0)

                    // БЛОКИРУЕМ КЛИК:
                    // Если клавиатура закрылась, мы "съедаем" это нажатие (return true),
                    // чтобы кнопки под пальцем (например, вкладка Звонки) НЕ нажались!
                    if (wasKeyboardOpen) {
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}