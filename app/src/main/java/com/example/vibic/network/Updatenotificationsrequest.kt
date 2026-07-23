package com.example.vibic.network

import com.google.gson.annotations.SerializedName

data class UpdateNotificationsRequest(
    @SerializedName("enabled") val enabled: Boolean
)