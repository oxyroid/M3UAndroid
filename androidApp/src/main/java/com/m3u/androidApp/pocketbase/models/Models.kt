package com.m3u.androidApp.pocketbase.models


import com.google.gson.annotations.SerializedName

data class SettingsResponse(
    @SerializedName("items") val items: List<Setting>
)

data class Setting(
    @SerializedName("id") val id: String,
    @SerializedName("message") val message: String,
    @SerializedName("days") val days: Int
)

data class DeviceResponse(
    @SerializedName("items") val items: List<Device>
)

data class Device(
    @SerializedName("id") val id: String,
    @SerializedName("android_id") val androidId: String,
    @SerializedName("model") val model: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("message") val message: String
)

data class DeviceCreateRequest(
    @SerializedName("android_id") val androidId: String,
    @SerializedName("model") val model: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("message") val message: String
)
