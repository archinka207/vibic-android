package com.example.vibic.network

import com.google.gson.annotations.SerializedName

data class FcmTokenRequest(
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_type") val deviceType: String = "android"
)