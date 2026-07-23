package com.example.vibic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.vibic.network.TokenManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "vibic_channel"
        private const val CHANNEL_NAME = "Vibic Уведомления"

        // Копим строки сообщений в памяти процесса, чтобы в шторке для одного
        // чата показывалось НЕСКОЛЬКО последних сообщений (как в самом чате),
        // а не только последнее перезатёртое.
        private val pendingLines = HashMap<String, MutableList<String>>()

        /**
         * Вызывать, когда пользователь открыл/прочитал чат: убирает уже
         * показанное уведомление по этому чату и очищает накопленные строки,
         * чтобы старые сообщения не "всплывали" в следующем пуше.
         */
        fun clearChatNotifications(context: Context, chatId: String?) {
            val key = chatId ?: "no_chat"
            pendingLines.remove(key)
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(key.hashCode())
            } catch (_: Exception) { }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔥 Новый FCM токен: $token")

        if (TokenManager.isAuthenticated) {
            PushTokenSyncManager.sendTokenToBackend(token)
        } else {
            Log.d(TAG, "⚠️ Пользователь не авторизован, новый токен не отправлен на сервер")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "📩 Получено сообщение: ${message.data}")

        val title  = message.notification?.title ?: message.data["title"] ?: "Новое сообщение"
        val body   = message.notification?.body  ?: message.data["body"]  ?: ""
        val chatId = message.data["chat_id"]

        // 1) Если этот чат сейчас открыт — пуш не нужен, сообщение и так
        //    придёт в UI через WebSocket.
        val openChatId = ChatDetailActivity.currentChatId
        if (!chatId.isNullOrEmpty() && chatId == openChatId) {
            Log.d(TAG, "🔕 Чат $chatId уже открыт — пуш подавлен")
            return
        }

        // 2) Если чат заглушён (mute) — не показываем пуш.
        if (!chatId.isNullOrEmpty() && isChatMuted(chatId)) {
            Log.d(TAG, "🔕 Чат $chatId заглушён — пуш подавлен")
            return
        }

        showNotification(title, body, chatId)
    }

    /** Читает локальный кэш чатов (тот же, что пишет MainFragment) и проверяет флаг mute. */
    private fun isChatMuted(chatId: String): Boolean {
        return try {
            val prefs = getSharedPreferences("chat_cache", Context.MODE_PRIVATE)
            val json = prefs.getString("chats", null) ?: return false
            val type = object : TypeToken<List<Chat>>() {}.type
            val chats: List<Chat> = Gson().fromJson(json, type) ?: return false
            chats.firstOrNull { it.id == chatId }?.isMuted ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось прочитать mute-флаг для $chatId", e)
            false
        }
    }

    private fun showNotification(title: String, body: String, chatId: String?) {
        createNotificationChannel()

        val intent = Intent(this, MainActivityApp::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat_id", chatId)
        }

        val notifKey = chatId ?: "no_chat"
        val requestCode = notifKey.hashCode()

        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Накопление строк для этого чата → одно уведомление со всеми
        // непрочитанными сообщениями (InboxStyle), как в самом чате.
        val lines = pendingLines.getOrPut(notifKey) { mutableListOf() }
        if (body.isNotBlank()) lines.add(body)
        // Не даём списку расти бесконечно — последние 8 строк.
        while (lines.size > 8) lines.removeAt(0)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(android.graphics.Color.parseColor("#6A11CB"))
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (lines.size > 1) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText("${lines.size} новых сообщений")
            lines.forEach { inbox.addLine(it) }
            builder.setStyle(inbox)
            // В свёрнутом виде показываем последнее сообщение + счётчик.
            builder.setContentText(lines.last())
            builder.setNumber(lines.size)
        } else {
            builder.setContentText(body)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(requestCode, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях Vibic"
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}