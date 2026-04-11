package com.example.vibic

data class ChatResponse(
    val id: String,
    val type: String,
    val name: String?,
    val avatar_url: String?,
    val created_at: String,
    val updated_at: String
)