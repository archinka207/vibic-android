package com.example.vibic.network

import com.google.gson.annotations.SerializedName

/**
 * Тело PATCH api/chats/{chatId}
 * Все поля опциональны — отправляются только изменённые.
 */
data class UpdateChatRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)