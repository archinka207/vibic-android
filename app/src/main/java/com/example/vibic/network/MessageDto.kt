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
    @SerializedName("sender") val sender: Sender? = null,
    // ── Reply fields ──────────────────────────────────────────────────────────
    @SerializedName("reply_to_id") val replyToId: String? = null,
    @SerializedName("reply_to") val replyTo: ReplyPreview? = null
)

data class Sender(
    @SerializedName("id") val id: String,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("avatar_url") val avatarUrl: String?
)

// Краткая инфа о сообщении на которое отвечают
data class ReplyPreview(
    @SerializedName("id") val id: String?,
    @SerializedName("content") val content: String?,
    @SerializedName("sender_id") val senderId: String?,
    @SerializedName("sender") val sender: Sender?
)