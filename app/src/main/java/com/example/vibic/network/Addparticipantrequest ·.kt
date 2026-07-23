package com.example.vibic.network

import com.google.gson.annotations.SerializedName

/**
 * Тело запроса POST api/chats/{chatId}/participants
 * Бэкенд ждёт user_id и role ("member" | "admin").
 */
data class AddParticipantRequest(
    @SerializedName("user_id") val user_id: String,
    @SerializedName("role") val role: String = "member"
)