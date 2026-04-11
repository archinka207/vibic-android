package com.example.vibic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.vibic.network.ApiClient
import com.example.vibic.network.FcmTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "vibic_channel"
        private const val CHANNEL_NAME = "Vibic Уведомления"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔥 Новый FCM токен: $token")

        // Отправляем токен на сервер
        sendTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "📩 Получено сообщение: ${message.data}")

        // Получаем данные из уведомления
        val title = message.notification?.title ?: message.data["title"] ?: "Новое сообщение"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val chatId = message.data["chat_id"]
        val messageId = message.data["message_id"]

        showNotification(title, body, chatId)
    }

    private fun sendTokenToServer(token: String) {
        runBlocking {
            try {
                val response = ApiClient.apiService.registerFcmToken(
                    FcmTokenRequest(fcmToken = token, deviceType = "android")
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Токен успешно отправлен на сервер")
                } else {
                    Log.e(TAG, "❌ Ошибка отправки токена: ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Error body: $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Исключение при отправке токена: ${e.message}")
            }
        }
    }

    private fun showNotification(title: String, body: String, chatId: String? = null) {
        val intent = Intent(this, MainActivityApp::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            chatId?.let {
                putExtra("open_chat_id", it)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}