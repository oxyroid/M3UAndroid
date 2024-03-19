package com.m3u.data.parser.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamInfo(
    @SerialName("server_info")
    val serverInfo: ServerInfo,
    @SerialName("user_info")
    val userInfo: UserInfo
) {
    @Serializable
    data class ServerInfo(
        @SerialName("https_port")
        val httpsPort: String?,
        @SerialName("port")
        val port: String?,
//        @SerialName("rtmp_port")
//        val rtmpPort: String?,
        @SerialName("server_protocol")
        val serverProtocol: String?,
//        @SerialName("time_now")
//        val timeNow: String?,
//        @SerialName("timestamp_now")
//        val timestampNow: Int?,
//        @SerialName("timezone")
//        val timezone: String?,
//        @SerialName("url")
//        val url: String?
    )

    @Serializable
    data class UserInfo(
//        @SerialName("active_cons")
//        val activeCons: String?,
        @SerialName("allowed_output_formats")
        val allowedOutputFormats: List<String>,
//        @SerialName("auth")
//        val auth: Int?,
//        @SerialName("created_at")
//        val createdAt: String?,
//        @SerialName("is_trial")
//        val isTrial: String?,
//        @SerialName("max_connections")
//        val maxConnections: String?,
//        @SerialName("message")
//        val message: String?,
//        @SerialName("password")
//        val password: String?,
//        @SerialName("status")
//        val status: String?,
//        @SerialName("username")
//        val username: String?
    )
}