package com.example.vibic

import android.content.Intent
import androidx.core.content.ContextCompat
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
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.vibic.network.TokenManager
import com.example.vibic.network.WebSocketManager
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

class ContactProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var avatarImage: ImageView
    private lateinit var avatarText: TextView
    private lateinit var onlineDot: View
    private lateinit var profileName: TextView
    private lateinit var profileStatus: TextView
    private lateinit var profilePhone: TextView
    private lateinit var usernameRow: LinearLayout
    private lateinit var profileUsername: TextView
    private lateinit var usernameCopyIcon: ImageView
    private var username: String? = null
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
        usernameRow       = findViewById(R.id.usernameRow)
        profileUsername   = findViewById(R.id.profileUsername)
        usernameCopyIcon  = findViewById(R.id.usernameCopyIcon)
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
                    // username есть только у пользователей — у вайбов и групп его нет
                    if (chat.type == "private") {
                        usernameRow.visibility = View.VISIBLE
                        loadUsername()
                    } else {
                        usernameRow.visibility = View.GONE
                    }
                }
            } catch (_: Exception) { /* тихо игнорируем */ }
        }
    }

    /** username собеседника — из участников чата (второй участник, не я). */
    private suspend fun loadUsername() {
        try {
            val resp = ApiClient.apiService.getParticipants(chatId)
            if (!resp.isSuccessful) return
            val myId = TokenManager.getUserId()
            val other = resp.body()?.firstOrNull { it.userId != myId }?.user ?: return
            showUsername(other.username)
            showAvatar(other.avatarUrl)
        } catch (_: Exception) { }
    }

    /** Фото собеседника с сервера. Не загрузилось — остаётся буква. */
    private fun showAvatar(url: String?) {
        if (url.isNullOrBlank()) return
        Glide.with(this).load(url).circleCrop()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
                ): Boolean = false

                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?,
                    dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    avatarImage.visibility = View.VISIBLE
                    avatarText.visibility = View.GONE
                    return false
                }
            })
            .into(avatarImage)
    }

    private fun showUsername(raw: String?) {
        val clean = raw?.trim()?.removePrefix("@")
        if (clean.isNullOrEmpty()) {
            username = null
            profileUsername.text = "username не указан"
            usernameCopyIcon.visibility = View.GONE
        } else {
            username = "@$clean"
            profileUsername.text = username
            usernameCopyIcon.visibility = View.VISIBLE
        }
    }

    private fun copyUsername() {
        val value = username ?: return
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("username", value))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun setOnlineStatus(online: Boolean) {
        if (online) {
            profileStatus.text = "в сети"
            profileStatus.setTextColor(MaterialColors.getColor(profileStatus, R.attr.vibicBrand))
            onlineDot.visibility = View.VISIBLE
        } else {
            profileStatus.text = "офлайн"
            profileStatus.setTextColor(MaterialColors.getColor(profileStatus, R.attr.vibicTextSecondary))
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

        usernameRow.setOnClickListener { copyUsername() }

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

        // Возвращаемся в уже открытый чат (он под профилем в стеке) и открываем поиск
        findViewById<LinearLayout>(R.id.btnSearch).setOnClickListener {
            startActivity(Intent(this, ChatDetailActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_name", chatName)
                putExtra(ChatDetailActivity.EXTRA_OPEN_SEARCH, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
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
            AlertDialog.Builder(this, R.style.VibicAlertDialog)
                .setTitle("Заблокировать")
                .setMessage("Заблокировать пользователя ${profileName.text}?")
                .setPositiveButton("Заблокировать") { _, _ ->
                    val blockedId = chatId.ifEmpty { profileName.text.toString() }
                    BlockedStore.add(this, blockedId, profileName.text.toString())
                    Toast.makeText(this, "Пользователь заблокирован", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        rowDeleteChat.setOnClickListener {
            AlertDialog.Builder(this, R.style.VibicAlertDialog)
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