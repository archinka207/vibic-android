package com.example.vibic

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.vibic.network.ApiClient
import com.example.vibic.network.WebSocketManager
import kotlinx.coroutines.launch

class ContactProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var avatarImage: ImageView
    private lateinit var avatarText: TextView
    private lateinit var onlineDot: View
    private lateinit var profileName: TextView
    private lateinit var profileStatus: TextView
    private lateinit var profilePhone: TextView
    private lateinit var aboutSection: LinearLayout
    private lateinit var aboutText: TextView
    private lateinit var notifStatus: TextView
    private lateinit var rowNotifications: LinearLayout
    private lateinit var rowBlock: LinearLayout
    private lateinit var rowDeleteChat: LinearLayout
    private lateinit var btnMessage: LinearLayout
    private lateinit var mediaAllButton: TextView   // ← кнопка "Все ›"

    private var chatId   = ""
    private var chatName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor   = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_contact_profile)

        chatId   = intent.getStringExtra("chat_id")   ?: ""
        chatName = intent.getStringExtra("chat_name") ?: "Контакт"

        initViews()
        populateBasicInfo()
        setupClickListeners()

        if (chatId.isNotEmpty()) loadChatDetails()
    }

    private fun initViews() {
        backButton        = findViewById(R.id.backButton)
        avatarImage       = findViewById(R.id.avatarImage)
        avatarText        = findViewById(R.id.avatarText)
        onlineDot         = findViewById(R.id.onlineDot)
        profileName       = findViewById(R.id.profileName)
        profileStatus     = findViewById(R.id.profileStatus)
        profilePhone      = findViewById(R.id.profilePhone)
        aboutSection      = findViewById(R.id.aboutSection)
        aboutText         = findViewById(R.id.aboutText)
        notifStatus       = findViewById(R.id.notifStatus)
        rowNotifications  = findViewById(R.id.rowNotifications)
        rowBlock          = findViewById(R.id.rowBlock)
        rowDeleteChat     = findViewById(R.id.rowDeleteChat)
        btnMessage        = findViewById(R.id.btnMessage)
        mediaAllButton    = findViewById(R.id.mediaAllButton)
    }

    private fun populateBasicInfo() {
        profileName.text  = chatName
        avatarText.text   = chatName.take(1).uppercase()
    }

    private fun loadChatDetails() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getChat(chatId)
                if (response.isSuccessful) {
                    val chat = response.body() ?: return@launch
                    val displayName = chat.name ?: chatName
                    profileName.text = displayName
                    avatarText.text  = displayName.take(1).uppercase()
                    setOnlineStatus(WebSocketManager.shared.isConnectedValue)
                }
            } catch (_: Exception) { /* тихо игнорируем */ }
        }
    }

    private fun setOnlineStatus(online: Boolean) {
        if (online) {
            profileStatus.text = "в сети"
            profileStatus.setTextColor(Color.parseColor("#B366FF"))
            onlineDot.visibility = View.VISIBLE
        } else {
            profileStatus.text = "офлайн"
            profileStatus.setTextColor(Color.parseColor("#888888"))
            onlineDot.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {

        findViewById<ImageView>(R.id.editButton).setOnClickListener {
            val i = Intent(this, EditContactActivity::class.java).apply {
                putExtra(EditContactActivity.EXTRA_CHAT_ID, chatId)
                putExtra(EditContactActivity.EXTRA_NAME, profileName.text.toString())
            }
            startActivity(i)
        }

        backButton.setOnClickListener { finish() }

        btnMessage.setOnClickListener { finish() }

        // ✅ Переход на страницу медиа
        mediaAllButton.setOnClickListener {
            startActivity(
                Intent(this, ChatMediaActivity::class.java).apply {
                    putExtra(ChatMediaActivity.EXTRA_CHAT_ID,   chatId)
                    putExtra(ChatMediaActivity.EXTRA_CHAT_NAME, chatName)
                }
            )
        }

        findViewById<LinearLayout>(R.id.btnSearch).setOnClickListener {
            Toast.makeText(this, "Поиск в разработке", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener {
            Toast.makeText(this, "Поделиться в разработке", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.btnMute).setOnClickListener {
            Toast.makeText(this, "Уведомления в разработке", Toast.LENGTH_SHORT).show()
        }

        rowNotifications.setOnClickListener {
            if (notifStatus.text == "Вкл") {
                notifStatus.text = "Выкл"
                Toast.makeText(this, "Уведомления выключены", Toast.LENGTH_SHORT).show()
            } else {
                notifStatus.text = "Вкл"
                Toast.makeText(this, "Уведомления включены", Toast.LENGTH_SHORT).show()
            }
        }

        rowBlock.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Заблокировать")
                .setMessage("Заблокировать пользователя ${profileName.text}?")
                .setPositiveButton("Заблокировать") { _, _ ->
                    Toast.makeText(this, "Пользователь заблокирован", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        rowDeleteChat.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Удалить чат")
                .setMessage("Вы уверены, что хотите удалить чат с ${profileName.text}?")
                .setPositiveButton("Удалить") { _, _ ->
                    val intent = Intent(this, MainActivityApp::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    Toast.makeText(this, "Чат удалён", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }
}