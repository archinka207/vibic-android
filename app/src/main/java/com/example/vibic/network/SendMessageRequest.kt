package com.example.vibic.network

import com.google.gson.annotations.SerializedName

data class SendMessageRequest(
    @SerializedName("content") val content: String,
    @SerializedName("type") val type: String = "text",
    @SerializedName("reply_to_id") val replyToId: String? = null
)