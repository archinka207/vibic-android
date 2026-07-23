package com.example.vibic

data class Chat(
    val id: String,
    val name: String,
    val lastMessage: String,
    val lastMessageTime: String,
    val avatarUrl: String? = null,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val type: String = "private",
    val otherUserId: String? = null,
    // ── Локальные флаги действий со списком чатов ──
    // Хранятся в том же кэше SharedPreferences ("chats"), парсятся Gson'ом.
    // Значения по умолчанию => старый закэшированный JSON без этих полей читается без ошибок.
    val isMuted: Boolean = false,
    val isPinned: Boolean = false
)