package com.example.vibic.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class WebSocketMessage(
    val type: String,
    val rawJson: String
)

/**
 * Управление WebSocket-соединением.
 *
 * Архитектура:
 * - connect() звать только: при логине, при ON_START (foreground), при появлении сети.
 * - disconnect() звать только: при логауте, при ON_STOP (background), при потере сети.
 * - Внутренний reconnect срабатывает только при неожиданном обрыве, с экспоненциальным backoff.
 * - Никаких пингов поверх OkHttp pingInterval — OkHttp сам шлёт WebSocket ping/pong
 *   и порвёт соединение, если pong не пришёл за 2×pingInterval.
 */
class WebSocketManager private constructor() {

    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null

    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val manualDisconnect = AtomicBoolean(false)
    private val networkAvailable = AtomicBoolean(true) // По умолчанию считаем что сеть есть

    /** Отметка момента последнего успешного соединения — для watchdog. */
    private val lastSuccessfulConnectMs = AtomicLong(0L)

    val isConnectedValue: Boolean get() = isConnected.get()

    /** Реактивное состояние подключения. UI может подписаться и обновляться мгновенно. */
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private val subscribedChats = mutableSetOf<String>()

    private val _messagesFlow = MutableSharedFlow<WebSocketMessage>(extraBufferCapacity = 100)
    val messagesFlow = _messagesFlow.asSharedFlow()

    private val gson = Gson()

    companion object {
        val shared = WebSocketManager()

        private const val WS_URL = "ws://176.123.162.135:8080/ws/messages"
        private const val TAG = "WebSocketManager"

        // Backoff: 1с → 2с → 4с → 8с → 16с → 30с (потолок)
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L

        // Pings: OkHttp шлёт ping каждые 20с, если pong не придёт за 40с — обрыв
        private const val PING_INTERVAL_SEC = 20L
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /** Запросить подключение. Идемпотентно: повторные вызовы безопасны. */
    fun connect() {
        if (!networkAvailable.get()) {
            Log.d(TAG, "🚫 connect() пропущен — нет сети")
            return
        }
        if (TokenManager.getAccessToken().isNullOrBlank() && TokenManager.getRefreshToken().isNullOrBlank()) {
            Log.d(TAG, "🚫 connect() пропущен — пользователь не залогинен")
            return
        }
        if (isConnected.get()) {
            Log.d(TAG, "✓ connect() пропущен — уже подключено")
            return
        }
        if (!isConnecting.compareAndSet(false, true)) {
            Log.d(TAG, "✓ connect() пропущен — уже подключаемся")
            return
        }

        manualDisconnect.set(false)
        // Отменим запланированный reconnect — мы и так подключаемся прямо сейчас
        reconnectJob?.cancel()
        reconnectJob = null

        scope.launch {
            doConnect()
        }
    }

    /** Принудительный полный сброс и пере-подключение. Звать при ON_START или onAvailable сети. */
    fun forceReconnect() {
        Log.d(TAG, "🔄 forceReconnect()")
        teardown(reason = "forceReconnect")
        connect()
    }

    /** Корректное отключение. Звать при логауте, при ON_STOP, при onLost сети. */
    fun disconnect() {
        Log.d(TAG, "🛑 disconnect()")
        manualDisconnect.set(true)
        teardown(reason = "manual disconnect")
    }

    /** Уведомление из VibicApp при изменении состояния сети. */
    fun onNetworkAvailable() {
        Log.d(TAG, "🌐 Сеть появилась")
        networkAvailable.set(true)
        if (!manualDisconnect.get()) {
            // Сеть появилась — попробуем подключиться сразу, не ждём backoff
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempts = 0
            connect()
        }
    }

    fun onNetworkLost() {
        Log.d(TAG, "🚫 Сеть потеряна")
        networkAvailable.set(false)
        // Не зовём disconnect() — это пометило бы как manualDisconnect.
        // Просто разрываем текущий сокет; reconnect не запустится из-за networkAvailable=false.
        teardownSocketOnly("network lost")
    }

    fun subscribeToChat(chatId: String) {
        subscribedChats.add(chatId)
        subscribeToChatInternal(chatId)
    }

    fun unsubscribeFromChat(chatId: String) {
        subscribedChats.remove(chatId)
        if (isConnected.get()) {
            sendJson(mapOf("type" to "unsubscribe", "chat_id" to chatId))
        }
    }

    fun sendMessage(chatId: String, content: String): Boolean {
        if (!isConnected.get()) return false
        return sendJson(mapOf("type" to "message", "chat_id" to chatId, "content" to content))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INTERNAL
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun doConnect() {
        try {
            val tokenReady = TokenManager.refreshTokenIfNeeded()
            if (!tokenReady) {
                Log.e(TAG, "❌ Не удалось обновить токен перед WebSocket connect")
                isConnecting.set(false)
                scheduleReconnect()
                return
            }

            val token = TokenManager.getAccessToken()
            if (token.isNullOrBlank()) {
                Log.e(TAG, "❌ Нет access token для WebSocket")
                isConnecting.set(false)
                scheduleReconnect()
                return
            }

            // Переиспользуем один и тот же OkHttpClient — у него свой пул и dispatcher
            val client = okHttpClient ?: OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // 0 = бесконечно, WS длинноживущий
                .pingInterval(PING_INTERVAL_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false) // Сами рулим переподключением
                .build()
                .also { okHttpClient = it }

            val request = Request.Builder()
                .url("$WS_URL?token=$token")
                .build()

            Log.d(TAG, "🔌 Подключение к $WS_URL...")
            webSocket = client.newWebSocket(request, createListener())
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка запуска WebSocket: ${e.message}", e)
            isConnecting.set(false)
            scheduleReconnect()
        }
    }

    private fun createListener(): WebSocketListener = object : WebSocketListener() {

        /** События от сокета, который уже не актуален, игнорируем. */
        private fun isStale(ws: WebSocket) = ws !== this@WebSocketManager.webSocket

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (isStale(webSocket)) { webSocket.cancel(); return }
            Log.d(TAG, "✅ WebSocket connected!")
            isConnected.set(true)
            isConnecting.set(false)
            reconnectAttempts = 0
            lastSuccessfulConnectMs.set(System.currentTimeMillis())
            _connectionState.value = true

            sendJson(mapOf("type" to "hello", "client" to "android"))
            subscribedChats.forEach { chatId -> subscribeToChatInternal(chatId) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isStale(webSocket)) return
            try {
                val json = JsonParser.parseString(text).asJsonObject
                val type = when {
                    json.has("type") -> json.get("type").asString
                    json.has("event") -> json.get("event").asString
                    else -> "unknown"
                }
                if (type == "ping" || type == "pong") return
                scope.launch { _messagesFlow.emit(WebSocketMessage(type, text)) }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: ${e.message}", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "🔌 WebSocket closing: code=$code, reason=$reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "🔌 WebSocket closed: code=$code, reason=$reason")
            if (isStale(webSocket)) return
            handleDisconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "❌ WebSocket failure: ${t.message}, code=${response?.code}", t)
            if (isStale(webSocket)) return
            handleDisconnect()
        }
    }

    private fun handleDisconnect() {
        isConnected.set(false)
        isConnecting.set(false)
        webSocket = null
        _connectionState.value = false

        if (!manualDisconnect.get() && networkAvailable.get()) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (manualDisconnect.get()) return
        if (!networkAvailable.get()) {
            Log.d(TAG, "⏸ Reconnect отложен — нет сети, ждём onNetworkAvailable()")
            return
        }

        reconnectJob?.cancel()

        // Экспоненциальный backoff: 1с, 2с, 4с, 8с, 16с, 30с, 30с...
        val delayMs = (MIN_BACKOFF_MS shl reconnectAttempts.coerceAtMost(5))
            .coerceAtMost(MAX_BACKOFF_MS)
        reconnectAttempts++

        Log.d(TAG, "🔄 Reconnect запланирован через ${delayMs}мс (попытка $reconnectAttempts)")

        reconnectJob = scope.launch {
            delay(delayMs)
            if (!isConnected.get() && !isConnecting.get()
                && !manualDisconnect.get() && networkAvailable.get()) {
                Log.d(TAG, "🔄 Попытка переподключения ($reconnectAttempts)...")
                connect()
            }
        }
    }

    /** Закрывает только сокет, не трогая флаги manualDisconnect. */
    private fun teardownSocketOnly(reason: String) {
        val old = webSocket
        webSocket = null // сначала обнуляем — колбэки старого сокета станут stale
        try {
            old?.close(1000, reason)
            old?.cancel() // жёстко освобождаем ресурсы, если close не прошёл
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка закрытия сокета: $reason", e)
        }
        isConnected.set(false)
        isConnecting.set(false)
        _connectionState.value = false
    }

    /** Полный teardown: сокет, reconnect-job. */
    private fun teardown(reason: String) {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        teardownSocketOnly(reason)
    }

    private fun subscribeToChatInternal(chatId: String) {
        if (isConnected.get()) {
            sendJson(mapOf("type" to "subscribe", "chat_id" to chatId))
        }
    }

    private fun sendJson(data: Map<String, Any>): Boolean {
        return try {
            val json = gson.toJson(data)
            webSocket?.send(json) == true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка отправки WebSocket JSON: ${e.message}", e)
            false
        }
    }
}