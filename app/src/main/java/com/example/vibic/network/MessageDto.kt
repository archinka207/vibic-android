package com.example.vibic.network

import com.google.gson.annotations.SerializedName

data class MessageDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("chat_id") val chatId: String? = null,
    @SerializedName("sender_id") val senderId: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("sender") val sender: Sender? = null
)

data class Sender(
    @SerializedName("id") val id: String,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("avatar_url") val avatarUrl: String?
)