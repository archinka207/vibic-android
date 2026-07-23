package com.example.vibic.network

import com.google.gson.annotations.SerializedName

// MARK: - Auth Models
data class SendCodeRequest(@SerializedName("phone") val phone: String)

data class VerifyCodeRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("code") val code: String
)

data class VerifyCodeResponse(
    @SerializedName("status") val status: String, // "success" или "2fa_required"
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("temp_token") val tempToken: String?,
    @SerializedName("expires_in") val expiresIn: Int?,
    @SerializedName("is_new_user") val isNewUser: Boolean?,
    @SerializedName("user_id") val userId: String?  // ИСПРАВЛЕНИЕ: Ожидаем user_id с сервера
)

data class Verify2FARequest(
    @SerializedName("temp_token") val tempToken: String,
    @SerializedName("password") val password: String
)

data class Setup2FARequest(
    @SerializedName("password") val password: String,
    @SerializedName("backup_email") val backupEmail: String? = null
)

data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)
data class LogoutRequest(@SerializedName("refresh_token") val refreshToken: String)

// ИСПРАВЛЕНИЕ: Добавлена модель для обновления профиля
data class UpdateProfileRequest(
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("status") val status: String? = null
)

// MARK: - Error Model
data class ErrorResponse(@SerializedName("error") val error: String)

// MARK: - Media Models
data class UploadResponse(
    @SerializedName("id") val id: String,
    @SerializedName("url") val url: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_size") val fileSize: Int,
    @SerializedName("mime_type") val mimeType: String
)