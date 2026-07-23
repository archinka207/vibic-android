package com.example.vibic.network

import com.example.vibic.ChatResponse
import com.example.vibic.CreateChatRequest
import com.example.vibic.CreatePrivateChatRequest
import com.example.vibic.CreateGroupChatRequest
import com.example.vibic.CreateChannelRequest
import com.example.vibic.UserResponse
import retrofit2.Response
import retrofit2.http.*
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface ApiService {

    // ================= AUTH =================

    @POST("auth/send-code")
    suspend fun sendCode(@Body request: SendCodeRequest): Response<Unit>

    @POST("auth/verify-code")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): Response<VerifyCodeResponse>

    @POST("auth/verify-2fa")
    suspend fun verify2FA(@Body request: Verify2FARequest): Response<VerifyCodeResponse>

    @POST("api/auth/setup-2fa")
    suspend fun setup2FA(@Body request: Setup2FARequest): Response<Unit>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<VerifyCodeResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>


    // ================= USER =================

    @GET("api/user/profile")
    suspend fun getCurrentUser(): Response<UserResponse>

    @PUT("api/user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<Unit>


    // ================= MEDIA =================

    @Multipart
    @POST("api/media/upload")
    suspend fun uploadMedia(
        @Part("file_type") fileType: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>


    // ================= CHATS =================

    @POST("api/chats/private")
    suspend fun createPrivateChat(@Body request: CreatePrivateChatRequest): Response<ChatResponse>

    @POST("api/chats/group")
    suspend fun createGroupChat(@Body request: CreateGroupChatRequest): Response<ChatResponse>

    @POST("api/chats/channel")
    suspend fun createChannel(@Body request: CreateChannelRequest): Response<ChatResponse>

    @GET("api/chats")
    suspend fun getChats(): Response<List<ChatDto>>
    @POST("api/chats/{chatId}/participants")
    suspend fun addParticipant(
        @Path("chatId") chatId: String,
        @Body request: AddParticipantRequest
    ): Response<Unit>
    @POST("api/chats/{chatId}/participants/{userId}/role")
    suspend fun updateParticipantRole(
        @Path("chatId") chatId: String,
        @Path("userId") userId: String,
        @Body request: UpdateRoleRequest
    ): Response<Unit>
    @PUT("api/chats/{chatId}/notifications")
    suspend fun updateNotifications(
        @Path("chatId") chatId: String,
        @Body request: UpdateNotificationsRequest
    ): Response<Unit>

    @PATCH("api/chats/{chatId}")
    suspend fun updateChat(
        @Path("chatId") chatId: String,
        @Body request: UpdateChatRequest
    ): Response<ChatDto>

    @DELETE("api/chats/{chatId}")
    suspend fun deleteChat(
        @Path("chatId") chatId: String
    ): Response<Unit>


    // (опционально, если захочешь удалять участников)
    @DELETE("api/chats/{chatId}/participants/{userId}")
    suspend fun removeParticipant(
        @Path("chatId") chatId: String,
        @Path("userId") userId: String
    ): Response<Unit>
    @GET("api/chats/{chatId}")
    suspend fun getChat(@Path("chatId") chatId: String): Response<ChatDto>
    @GET("api/chats/{chatId}/participants")
    suspend fun getParticipants(
        @Path("chatId") chatId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<ParticipantDto>>

    @POST("api/chats/{chatId}/leave")
    suspend fun leaveChat(
        @Path("chatId") chatId: String
    ): Response<Unit>

//    @POST("api/chats")
//    suspend fun createChat(@Body request: CreateChatRequest): Response<ChatResponse>


    // ================= MESSAGES =================

    @GET("api/chats/{chatId}/messages")
    suspend fun getMessages(@Path("chatId") chatId: String): Response<List<MessageDto>>

    @POST("api/chats/{chatId}/messages")
    suspend fun sendMessage(
        @Path("chatId") chatId: String,
        @Body request: SendMessageRequest
    ): Response<MessageDto>

    @DELETE("api/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("messageId") messageId: String
    ): Response<Unit>


    // ================= USERS =================

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<UserResponse>>


    // ================= PUSH =================

    @POST("api/user/device-token")
    suspend fun registerFcmToken(
        @Body request: FcmTokenRequest
    ): Response<Unit>
}