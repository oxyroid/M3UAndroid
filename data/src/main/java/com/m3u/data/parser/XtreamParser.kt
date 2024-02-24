package com.m3u.data.parser

import com.m3u.data.database.model.Stream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

interface XtreamParser : Parser<XtreamInput, XtreamOutput>

data class XtreamInput(
    val address: String, // scheme + host + port
    val username: String,
    val password: String
) {
    companion object {
        fun decodeFromUrl(url: String): XtreamInput {
            val httpUrl = url.toHttpUrl()
            return XtreamInput(
                address = "${httpUrl.scheme}://${httpUrl.host}:${httpUrl.port}",
                username = httpUrl.queryParameter("username").orEmpty(),
                password = httpUrl.queryParameter("password").orEmpty()
            )
        }

        fun encodeToUrl(input: XtreamInput): String {
            return with(input) { "$address/player_api.php?username=$username&password=$password" }
        }
    }
}

data class XtreamOutput(
    val lives: List<XtreamData> = emptyList(),
    val vod: List<XtreamData> = emptyList(),
    val series: List<XtreamData> = emptyList(),
    val allowedOutputFormats: List<String> = emptyList()
)

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
        @SerialName("rtmp_port")
        val rtmpPort: String?,
        @SerialName("server_protocol")
        val serverProtocol: String?,
        @SerialName("time_now")
        val timeNow: String?,
        @SerialName("timestamp_now")
        val timestampNow: Int?,
        @SerialName("timezone")
        val timezone: String?,
        @SerialName("url")
        val url: String?
    )

    @Serializable
    data class UserInfo(
        @SerialName("active_cons")
        val activeCons: String?,
        @SerialName("allowed_output_formats")
        val allowedOutputFormats: List<String>,
        @SerialName("auth")
        val auth: Int?,
        @SerialName("created_at")
        val createdAt: String?,
        @SerialName("is_trial")
        val isTrial: String?,
        @SerialName("max_connections")
        val maxConnections: String?,
        @SerialName("message")
        val message: String?,
        @SerialName("password")
        val password: String?,
        @SerialName("status")
        val status: String?,
        @SerialName("username")
        val username: String?
    )
}

@Serializable
data class XtreamData(
    @SerialName("added")
    val added: String?,
    @SerialName("category_id")
    val categoryId: String?,
    @SerialName("custom_sid")
    val customSid: String?,
    @SerialName("direct_source")
    val directSource: String?,
    @SerialName("epg_channel_id")
    val epgChannelId: String?,
    @SerialName("name")
    val name: String?,
    @SerialName("num")
    val num: Int?,
    @SerialName("stream_icon")
    val streamIcon: String?,
    @SerialName("stream_id")
    val streamId: Int?,
    @SerialName("stream_type")
    val streamType: String?,
    @SerialName("tv_archive")
    val tvArchive: Int?,
    @SerialName("tv_archive_duration")
    val tvArchiveDuration: Int?
)

fun XtreamData.toStream(
    address: String,
    username: String,
    password: String,
    allowedOutputFormats: List<String>,
    playlistUrl: String
): Stream = Stream(
    url = "$address/$username/$password/$streamId.${allowedOutputFormats.first()}",
    group = "",
    title = name.orEmpty(),
    cover = streamIcon,
    playlistUrl = playlistUrl
)