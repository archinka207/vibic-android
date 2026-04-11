package com.example.vibic

data class UserResponse(
    val id: String,
    val phone: String? = null,
    val first_name: String?,
    val last_name: String?,
    val avatar_url: String?,
    val status: String?,
    val online: Boolean
)