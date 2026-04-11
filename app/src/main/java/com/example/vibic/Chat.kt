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
    val otherUserId: String? = null
)