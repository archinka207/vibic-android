package com.example.vibic.network

import com.google.gson.annotations.SerializedName

/**
 * Ответ GET api/chats/{chatId}/participants
 * Сервер отдаёт массив таких объектов.
 */
data class ParticipantDto(
    @SerializedName("user_id") val userId: String,
    /** "owner" | "admin" | "member" */
    @SerializedName("role") val role: String = "member",
    @SerializedName("joined_at") val joinedAt: String? = null,
    @SerializedName("user") val user: ParticipantUserDto? = null
)

data class ParticipantUserDto(
    @SerializedName("id") val id: String,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    // Сервер пока не отдаёт — поле ждёт доработки бэка
    @SerializedName("username") val username: String? = null,
    @SerializedName("online") val online: Boolean = false
)