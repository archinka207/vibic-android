package com.example.vibic.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Теперь передаем сырой JSON, чтобы ничего не сломать!
data class WebSocketMessage(
    val type: String,
    val rawJson: String
)

class WebSocketManager private constructor() {

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)

    val isConnectedValue: Boolean get() = isConnected.get()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempts = 0

    private val subscribedChats = mutableSetOf<String>()

    private val _messagesFlow = MutableSharedFlow<WebSocketMessage>(extraBufferCapacity = 100)
    val messagesFlow = _messagesFlow.asSharedFlow()

    private val gson = Gson()

    companion object {
        val shared = WebSocketManager()
        private const val BASE_URL = "http://176.123.162.135:8080"
        private const val WS_URL = "ws://176.123.162.135:8080/ws/messages"
        private const val TAG = "WebSocketManager"
    }

    fun connect() {
        if (isConnecting.get() || isConnected.get()) return

        val token = TokenManager.getAccessToken()
        if (token.isNullOrBlank()) return

        isConnecting.set(true)
        val url = "$WS_URL?token=$token"

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, createListener())
    }

    private fun createListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket connected!")
                isConnected.set(true)
                isConnecting.set(false)
                reconnectAttempts = 0

                sendJson(mapOf("type" to "hello", "client" to "android"))
                subscribedChats.forEach { subscribeToChatInternal(it) }
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📨 Received: $text")
                try {
                    val json = JsonParser.parseString(text).asJsonObject
                    val type = if (json.has("type")) json.get("type").asString
                    else if (json.has("event")) json.get("event").asString
                    else "unknown"

                    if (type == "ping") { sendPong(); return }
                    if (type == "pong") return

                    // Отправляем чистый текст как есть
                    scope.launch { _messagesFlow.emit(WebSocketMessage(type, text)) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: ${e.message}")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect()
            }
        }
    }

    private fun handleDisconnect() {
        isConnected.set(false)
        isConnecting.set(false)
        stopHeartbeat()
        scheduleReconnect()
    }

    fun subscribeToChat(chatId: String) {
        subscribedChats.add(chatId)
        subscribeToChatInternal(chatId)
    }

    private fun subscribeToChatInternal(chatId: String) {
        if (isConnected.get()) sendJson(mapOf("type" to "subscribe", "chat_id" to chatId))
    }

    fun unsubscribeFromChat(chatId: String) {
        subscribedChats.remove(chatId)
        if (isConnected.get()) sendJson(mapOf("type" to "unsubscribe", "chat_id" to chatId))
    }

    fun sendMessage(chatId: String, content: String): Boolean {
        if (!isConnected.get()) return false
        return sendJson(mapOf("type" to "message", "chat_id" to chatId, "content" to content))
    }

    private fun sendJson(data: Map<String, Any>): Boolean {
        return try {
            webSocket?.send(gson.toJson(data))
            true
        } catch (e: Exception) { false }
    }

    private fun sendPong() { sendJson(mapOf("type" to "pong")) }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isConnected.get()) {
                delay(25000)
                if (isConnected.get()) sendJson(mapOf("type" to "ping"))
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val delayMs = (reconnectAttempts * 2000L).coerceAtMost(15000L)
        reconnectAttempts++
        reconnectJob = scope.launch {
            delay(delayMs)
            TokenManager.refreshTokenIfNeeded()
            if (!isConnected.get() && !isConnecting.get()) connect()
        }
    }

    fun disconnect() {
        stopHeartbeat()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected.set(false)
        isConnecting.set(false)
    }

    fun forceReconnect() {
        disconnect()
        reconnectAttempts = 0
        connect()
    }
}