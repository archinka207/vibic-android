package com.example.vibic

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vibic.network.ApiClient
import com.example.vibic.network.MessageDto
import com.example.vibic.network.SendMessageRequest
import com.example.vibic.network.TokenManager
import com.example.vibic.network.WebSocketManager
import com.example.vibic.network.WebSocketMessage
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ChatDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageView
    private lateinit var backButton: ImageView
    private lateinit var chatUserName: TextView
    private lateinit var chatUserStatus: TextView

    private lateinit var adapter: MessageAdapter
    private var chatId = ""
    private var myUserId: String = ""
    private var userName: String = ""

    private val allMessages = mutableListOf<MessageDto>()
    private val sentMessageIds = mutableSetOf<String>()
    private val processedMessageIds = mutableSetOf<String>()

    private var webSocketJob: Job? = null
    private var connectionCheckJob: Job? = null

    private val gson = Gson()
    private var isSubscribed = false
    private var isFirstLoad = true

    companion object {
        private const val TAG = "ChatDetailActivity"
        var currentChatId: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_detail)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)



        chatId = intent.getStringExtra("chat_id") ?: run {
            Toast.makeText(this, "Ошибка: ID чата не передан", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        userName = intent.getStringExtra("chat_name") ?: "Чат"
        currentChatId = chatId

        myUserId = TokenManager.getUserId() ?: ""
        val isLikelyPhoneNumber =
            myUserId.isNotEmpty() && (myUserId.startsWith("+") || myUserId.all { it.isDigit() })

        if (myUserId.isEmpty() || isLikelyPhoneNumber) {
            val token = TokenManager.getAccessToken()
            if (token != null) {
                try {
                    val parts = token.split(".")
                    if (parts.size >= 2) {
                        val payload = String(
                            android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE)
                        )
                        val json = org.json.JSONObject(payload)
                        val sub = json.optString("sub")
                        val uid = json.optString("user_id")

                        val isSubPhone = sub.startsWith("+") || sub.all { it.isDigit() }
                        val finalId = when {
                            isSubPhone && uid.isNotEmpty() -> uid
                            uid.isNotEmpty() -> uid
                            else -> sub
                        }

                        if (finalId.isNotEmpty() && !(finalId.startsWith("+") || finalId.all { it.isDigit() })) {
                            myUserId = finalId
                            TokenManager.saveUserId(finalId)
                            Log.d(TAG, "UserId восстановлен из токена: $myUserId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка извлечения userId", e)
                }
            }
        }

        initViews()
        setupUI()
        setupRecyclerView()
        setupClickListeners()

        if (myUserId.isEmpty() || myUserId.startsWith("+") || myUserId.all { it.isDigit() }) {
            lifecycleScope.launch {
                try {
                    val response = ApiClient.apiService.getCurrentUser()
                    if (response.isSuccessful) {
                        response.body()?.id?.let { correctId ->
                            myUserId = correctId
                            TokenManager.saveUserId(correctId)
                            Log.d(TAG, "UserId восстановлен из сети: $myUserId")
                            runOnUiThread {
                                adapter = MessageAdapter(myUserId)
                                recyclerView.adapter = adapter
                                adapter.setMessages(allMessages)
                                if (adapter.itemCount > 0) {
                                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка восстановления userId из сети", e)
                }
            }
        }

        Log.d(TAG, "ChatId: $chatId, UserId: $myUserId")

        loadCachedMessages()
        loadMessagesInBackground()

        subscribeToWebSocket()

        if (WebSocketManager.shared.isConnectedValue) {
            WebSocketManager.shared.subscribeToChat(chatId)
            isSubscribed = true
            Log.d(TAG, "✅ Подписались на чат $chatId")
        }

        startWebSocketStatusMonitor()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.messagesRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        backButton = findViewById(R.id.backButton)
        chatUserName = findViewById(R.id.chatUserName)
        chatUserStatus = findViewById(R.id.chatUserStatus)
    }

    private fun setupUI() {
        chatUserName.text = userName
        updateConnectionStatus()
    }

    private fun updateConnectionStatus() {
        val isConnected = WebSocketManager.shared.isConnectedValue
        runOnUiThread {
            chatUserStatus.text = if (isConnected) "В сети" else "Офлайн"
            chatUserStatus.setTextColor(
                if (isConnected) android.graphics.Color.parseColor("#4CAF50")
                else android.graphics.Color.parseColor("#F44336")
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(myUserId)
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = adapter
    }

    private fun loadCachedMessages() {
        val prefs = getSharedPreferences("message_cache", MODE_PRIVATE)
        val cachedJson = prefs.getString("chat_$chatId", null)

        if (cachedJson != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<MessageDto>>() {}.type
                val messages: List<MessageDto> = gson.fromJson(cachedJson, type)

                allMessages.clear()
                processedMessageIds.clear()

                allMessages.addAll(messages)
                messages.forEach { it.id?.let { id -> processedMessageIds.add(id) } }
                allMessages.sortBy { it.createdAt }

                runOnUiThread {
                    adapter.setMessages(allMessages)
                    if (adapter.itemCount > 0) {
                        recyclerView.scrollToPosition(adapter.itemCount - 1)
                    }
                    Log.d(TAG, "Загружено ${allMessages.size} сообщений из кэша")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки кэша", e)
            }
        }
    }

    private fun saveMessagesToCache(messages: List<MessageDto>) {
        try {
            val json = gson.toJson(messages)
            val prefs = getSharedPreferences("message_cache", MODE_PRIVATE)
            prefs.edit().putString("chat_$chatId", json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения кэша", e)
        }
    }

    private fun loadMessagesInBackground() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🔄 Фоновая загрузка сообщений...")
                val response = ApiClient.apiService.getMessages(chatId)

                if (response.isSuccessful()) {
                    val apiMessages = response.body() ?: emptyList()
                    Log.d(TAG, "Загружено сообщений из API: ${apiMessages.size}")

                    allMessages.clear()
                    sentMessageIds.clear()
                    processedMessageIds.clear()

                    allMessages.addAll(apiMessages)
                    allMessages.sortBy { it.createdAt }
                    apiMessages.forEach { it.id?.let { id -> processedMessageIds.add(id) } }

                    saveMessagesToCache(apiMessages)

                    runOnUiThread {
                        adapter.setMessages(allMessages)
                        if (adapter.itemCount > 0 && isFirstLoad) {
                            recyclerView.scrollToPosition(adapter.itemCount - 1)
                            isFirstLoad = false
                        }
                        Log.d(TAG, "✅ Адаптер обновлен, сообщений: ${adapter.itemCount}")
                    }
                } else {
                    Log.e(TAG, "Ошибка загрузки: ${response.code()}")
                    if (response.code() == 401) {
                        Toast.makeText(this@ChatDetailActivity, "Сессия истекла", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки", e)
            }
        }
    }

    private fun subscribeToWebSocket() {
        webSocketJob?.cancel()
        webSocketJob = lifecycleScope.launch {
            WebSocketManager.shared.messagesFlow.collect { message ->
                when (message.type) {
                    "new_message", "message" -> {
                        handleNewMessage(message)
                    }

                    "connected", "connection_established", "hello" -> {
                        runOnUiThread {
                            updateConnectionStatus()
                            if (!isSubscribed && chatId.isNotEmpty()) {
                                WebSocketManager.shared.subscribeToChat(chatId)
                                isSubscribed = true
                                Log.d(TAG, "✅ Подписались на чат $chatId")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleNewMessage(message: com.example.vibic.network.WebSocketMessage) {
        try {
            // Парсим сырой JSON
            val json = com.google.gson.JsonParser.parseString(message.rawJson).asJsonObject

            // Ищем данные: сервер может положить их внутрь "payload", "message", "data" или просто в корень
            val messageJson = json.get("payload") ?: json.get("message") ?: json.get("data") ?: json

            val messageDto = gson.fromJson(messageJson, com.example.vibic.network.MessageDto::class.java)

            // Если сервер не прислал ID чата, подставляем текущий
            val incomingChatId = messageDto.chatId ?: chatId

            // Убеждаемся, что сообщение принадлежит текущему чату и оно не пустое
            if (incomingChatId == chatId && !messageDto.content.isNullOrEmpty()) {

                // Генерируем временный ID, если сервер его не прислал
                val messageId = messageDto.id ?: java.util.UUID.randomUUID().toString()
                val finalMessage = messageDto.copy(id = messageId, chatId = incomingChatId)

                // Если сообщение уже есть в списке (мы его отправили) - пропускаем
                if (processedMessageIds.contains(finalMessage.id)) return

                Log.d(TAG, "✨ Успешно распарсили сообщение: ${finalMessage.content}")
                addNewMessage(finalMessage)
                saveMessagesToCache(allMessages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка парсинга входящего сообщения: ${e.message}")
        }
    }

    private fun addNewMessage(message: com.example.vibic.network.MessageDto) {
        val messageId = message.id
        if (messageId != null) {
            if (processedMessageIds.contains(messageId)) return
            processedMessageIds.add(messageId)
        }

        val exists = allMessages.any { it.id == message.id }
        if (!exists) {
            Log.d(TAG, "✨ Новое сообщение: ${message.content}")

            // Просто добавляем в наш массив (без тяжелой пересортировки всего списка)
            allMessages.add(message)

            runOnUiThread {
                // Говорим адаптеру точечно добавить 1 элемент (это работает в 100 раз быстрее)
                adapter.addMessage(message)

                // Прокручиваем плавно вниз
                recyclerView.post {
                    if (adapter.itemCount > 0) {
                        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
        }
    }

    private fun startWebSocketStatusMonitor() {
        connectionCheckJob?.cancel()
        connectionCheckJob = lifecycleScope.launch {
            while (true) {
                delay(5000)
                runOnUiThread {
                    updateConnectionStatus()
                }
            }
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }

        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun sendMessage(text: String) {
        messageInput.text.clear()
        sendButton.isEnabled = false

        val tempId = UUID.randomUUID().toString()
        val currentTime = getCurrentTime()

        val tempMessage = MessageDto(
            id = tempId,
            chatId = chatId,
            senderId = myUserId,
            content = text,
            type = "text",
            createdAt = currentTime
        )

        allMessages.add(tempMessage)
        allMessages.sortBy { it.createdAt }
        sentMessageIds.add(tempId)
        processedMessageIds.add(tempId)

        runOnUiThread {
            adapter.setMessages(allMessages)
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.sendMessage(chatId, SendMessageRequest(text))

                if (response.isSuccessful()) {
                    val realMessage = response.body()

                    realMessage?.let {
                        val index = allMessages.indexOfFirst { msg -> msg.id == tempId }
                        if (index != -1) {
                            allMessages.removeAt(index)
                            processedMessageIds.remove(tempId)
                            sentMessageIds.remove(tempId)

                            if (!processedMessageIds.contains(it.id)) {
                                it.id?.let { id -> processedMessageIds.add(id) }
                                allMessages.add(it)
                                allMessages.sortBy { msg -> msg.createdAt }
                            }

                            runOnUiThread {
                                adapter.setMessages(allMessages)
                                recyclerView.scrollToPosition(adapter.itemCount - 1)
                            }

                            saveMessagesToCache(allMessages)
                        }
                    } ?: run {
                        allMessages.removeAll { it.id == tempId }
                        processedMessageIds.remove(tempId)
                        sentMessageIds.remove(tempId)

                        runOnUiThread {
                            adapter.setMessages(allMessages)
                            Toast.makeText(
                                this@ChatDetailActivity,
                                "Сервер вернул пустой ответ",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    val errorBody = try {
                        response.errorBody()?.string()
                    } catch (_: Exception) {
                        null
                    }

                    Log.e(TAG, "Ошибка отправки: code=${response.code()}, body=$errorBody")

                    allMessages.removeAll { it.id == tempId }
                    processedMessageIds.remove(tempId)
                    sentMessageIds.remove(tempId)

                    runOnUiThread {
                        adapter.setMessages(allMessages)
                        Toast.makeText(
                            this@ChatDetailActivity,
                            "Ошибка отправки: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Исключение при отправке сообщения", e)

                allMessages.removeAll { it.id == tempId }
                processedMessageIds.remove(tempId)
                sentMessageIds.remove(tempId)

                runOnUiThread {
                    adapter.setMessages(allMessages)
                    Toast.makeText(
                        this@ChatDetailActivity,
                        "Не удалось отправить сообщение: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                runOnUiThread {
                    sendButton.isEnabled = true
                }
            }
        }
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    override fun onDestroy() {
        super.onDestroy()

        if (currentChatId == chatId) {
            currentChatId = null
        }

        if (isSubscribed && chatId.isNotEmpty()) {
            WebSocketManager.shared.unsubscribeFromChat(chatId)
            isSubscribed = false
        }

        webSocketJob?.cancel()
        connectionCheckJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus()

        if (WebSocketManager.shared.isConnectedValue && !isSubscribed) {
            WebSocketManager.shared.subscribeToChat(chatId)
            isSubscribed = true
        }
    }
}