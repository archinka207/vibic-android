package com.example.vibic.network

import android.os.Build
import com.google.gson.annotations.SerializedName

data class FcmTokenRequest(
    @SerializedName("token") val token: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("device_name") val deviceName: String? = Build.MODEL
)