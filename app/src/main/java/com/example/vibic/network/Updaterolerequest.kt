package com.example.vibic.network

import com.google.gson.annotations.SerializedName

/**
 * Тело POST api/chats/{chatId}/participants/{userId}/role
 * Допустимые роли: "member" | "admin" | "subscriber" (owner назначить нельзя).
 */
data class UpdateRoleRequest(
    @SerializedName("role") val role: String
)