package com.example.vibic

data class CreateChatRequest(
    val type: String,
    val name: String?,
    val user_ids: List<String>
)