package com.m3u.data.parser.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamInfo(
    @SerialName("server_info")
    val serverInfo: ServerInfo = ServerInfo(),
    @SerialName("user_info")
    val userInfo: UserInfo = UserInfo()
) {
    @Serializable
    data class ServerInfo(
        @SerialName("https_port")
        val httpsPort: String? = null,
        @SerialName("port")
        val port: String? = null,
//        @SerialName("rtmp_port")
//        val rtmpPort: String?,
        @SerialName("server_protocol")
        val serverProtocol: String? = null,
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
        @SerialName("active_cons")
        val activeCons: String? = null,
        @SerialName("allowed_output_formats")
        val allowedOutputFormats: List<String> = emptyList(),
//        @SerialName("auth")
//        val auth: Int?,
        @SerialName("created_at")
        val createdAt: String? = null,
        @SerialName("is_trial")
        val isTrial: String? = null,
        @SerialName("max_connections")
        val maxConnections: String? = null,
//        @SerialName("message")
//        val message: String?,
//        @SerialName("password")
//        val password: String?,
        @SerialName("status")
        val status: String? = null,
        @SerialName("username")
        val username: String? = null
    )
}