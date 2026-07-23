package com.example.vibic.network

import com.google.gson.annotations.SerializedName

data class ChatDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String = "private",
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("last_message") val lastMessage: LastMessage? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0,

    // ── Поля группы ───────────────────────────────────────────────────────────
    // description и participants_count приходят из GET api/chats/{chatId}
    // (бэкенд отдаёт ChatDetailResponse с этими полями).
    @SerializedName("description") val description: String? = null,
    @SerializedName("participants_count") val participantsCount: Int? = null
)

data class LastMessage(
    @SerializedName("id") val id: String,
    @SerializedName("content") val content: String,
    @SerializedName("type") val type: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("sender_id") val senderId: String
)