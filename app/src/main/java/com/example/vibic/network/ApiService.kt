package com.example.vibic.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import com.example.vibic.UserResponse
import com.example.vibic.CreateChatRequest
import com.example.vibic.ChatResponse

interface ApiService {

    // --- Public Auth (без /api/) ---
    @POST("auth/send-code")
    suspend fun sendCode(@Body request: SendCodeRequest): Response<Unit>

    @POST("auth/verify-code")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): Response<VerifyCodeResponse>

    @POST("auth/verify-2fa")
    suspend fun verify2FA(@Body request: Verify2FARequest): Response<VerifyCodeResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<VerifyCodeResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    // --- Protected Auth (с /api/) ---
    @POST("api/auth/setup-2fa")
    suspend fun setup2FA(@Body request: Setup2FARequest): Response<Unit>

    // --- Users (с /api/) ---
    @GET("api/user/profile")
    suspend fun getCurrentUser(): Response<UserResponse>

    // --- Media Upload (с /api/) ---
    @Multipart
    @POST("api/media/upload")
    suspend fun uploadMedia(
        @Part("file_type") fileType: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    // --- Chat (с /api/) ---
    @GET("api/chats")
    suspend fun getChats(): Response<List<ChatDto>>

    @GET("api/chats/{chatId}")
    suspend fun getChat(@Path("chatId") chatId: String): Response<ChatDto>

    @GET("api/chats/{chatId}/messages")
    suspend fun getMessages(@Path("chatId") chatId: String): Response<List<MessageDto>>

    @POST("api/chats/{chatId}/messages")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Body request: SendMessageRequest
    ): Response<MessageDto>

    // --- Search Users (с /api/) ---
    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<UserResponse>>

    // --- Create Chat (с /api/) ---
    @POST("api/chats")
    suspend fun createChat(@Body request: CreateChatRequest): Response<ChatResponse>

    // --- Register device / FCM token (с /api/) ---
    @POST("api/user/device")
    suspend fun registerFcmToken(@Body request: FcmTokenRequest): Response<Unit>
}