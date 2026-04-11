package com.example.vibic

data class Message(
    val id: String,
    val text: String,
    val time: String,
    val isSentByMe: Boolean,
    val chatId: String = ""
)