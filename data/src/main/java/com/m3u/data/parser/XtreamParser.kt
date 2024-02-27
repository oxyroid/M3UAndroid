package com.m3u.data.parser

import com.m3u.core.util.basic.startsWithAny
import com.m3u.data.database.model.Stream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

interface XtreamParser : Parser<XtreamInput, XtreamOutput>

data class XtreamInput(
    val address: String, // scheme + host + port
    val username: String,
    val password: String,
    // DataSource.Xtream.TYPE_LIVE, DataSource.Xtream.TYPE_VOD, DataSource.Xtream.TYPE_SERIES
    val type: String? = null // null means all
) {
    companion object {
        fun decodeFromPlaylistUrl(url: String): XtreamInput {
            val hasScheme = url.startsWithAny("http:", "https:", ignoreCase = true)
            val httpUrl = if (hasScheme) url.toHttpUrl() else "http://$url".toHttpUrl()
            val username = httpUrl.queryParameter("username").orEmpty()
            val password = httpUrl.queryParameter("password").orEmpty()
            return XtreamInput(
                address = "${httpUrl.scheme}://${httpUrl.host}:${httpUrl.port}",
                username = username,
                password = password,
                type = httpUrl.queryParameter("type")
            )
        }

        fun decodeFromUrl(url: String): XtreamInput {
            val hasScheme = url.startsWithAny("http:", "https:", ignoreCase = true)
            val httpUrl = if (hasScheme) url.toHttpUrl() else "http://$url".toHttpUrl()
            val username = httpUrl.pathSegments.getOrNull(1).orEmpty()
            val password = httpUrl.pathSegments.getOrNull(2).orEmpty()
            return XtreamInput(
                address = "${httpUrl.scheme}://${httpUrl.host}:${httpUrl.port}",
                username = username,
                password = password,
                type = httpUrl.queryParameter("type")
            )
        }

        fun encodeToUrl(input: XtreamInput): String {
            return with(input) {
                buildString {
                    append("$address/player_api.php?username=$username&password=$password&")
                    if (type != null) {
                        append("type=$type")
                    }
                }
            }
        }

        fun decodeFromPlaylistUrlOrNull(url: String): XtreamInput? =
            runCatching { decodeFromPlaylistUrl(url) }.getOrNull()

        fun decodeFromUrlOrNull(url: String): XtreamInput? =
            runCatching { decodeFromUrl(url) }.getOrNull()
    }
}

data class XtreamOutput(
    val lives: List<XtreamLive> = emptyList(),
    val vods: List<XtreamVod> = emptyList(),
    val series: List<XtreamSerial> = emptyList(),
    val liveCategories: List<XtreamCategory> = emptyList(),
    val vodCategories: List<XtreamCategory> = emptyList(),
    val serialCategories: List<XtreamCategory> = emptyList(),
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
data class XtreamLive(
    @SerialName("added")
    val added: String?,
    @SerialName("category_id")
    val categoryId: Int?,
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

@Serializable
data class XtreamVod(
    @SerialName("added")
    val added: String?,
    @SerialName("category_id")
    val categoryId: Int?,
    @SerialName("container_extension")
    val containerExtension: String?,
    @SerialName("custom_sid")
    val customSid: String?,
    @SerialName("direct_source")
    val directSource: String?,
    @SerialName("name")
    val name: String?,
    @SerialName("num")
    val num: Int?,
    @SerialName("rating")
    val rating: String?,
    @SerialName("rating_5based")
    val rating5based: Double?,
    @SerialName("stream_icon")
    val streamIcon: String?,
    @SerialName("stream_id")
    val streamId: Int?,
    @SerialName("stream_type")
    val streamType: String?
)

@Serializable
data class XtreamSerial(
    @SerialName("backdrop_path")
    val backdropPath: List<String>,
    @SerialName("cast")
    val cast: String?,
    @SerialName("category_id")
    val categoryId: Int?,
    @SerialName("cover")
    val cover: String?,
    @SerialName("director")
    val director: String?,
    @SerialName("episode_run_time")
    val episodeRunTime: String?,
    @SerialName("genre")
    val genre: String?,
    @SerialName("last_modified")
    val lastModified: String?,
    @SerialName("name")
    val name: String?,
    @SerialName("num")
    val num: Int?,
    @SerialName("plot")
    val plot: String?,
    @SerialName("rating")
    val rating: String?,
    @SerialName("rating_5based")
    val rating5based: Int?,
    @SerialName("releaseDate")
    val releaseDate: String?,
    @SerialName("series_id")
    val seriesId: Int?,
    @SerialName("youtube_trailer")
    val youtubeTrailer: String?
)

fun XtreamLive.toStream(
    address: String,
    username: String,
    password: String,
    allowedOutputFormats: List<String>,
    playlistUrl: String,
    category: String
): Stream = Stream(
    url = "$address/$streamType/$username/$password/$streamId.${allowedOutputFormats.first()}",
    group = category,
    title = name.orEmpty(),
    cover = streamIcon,
    playlistUrl = playlistUrl
)

fun XtreamVod.toStream(
    address: String,
    username: String,
    password: String,
    playlistUrl: String,
    category: String
): Stream = Stream(
    url = "$address/movie/$username/$password/$streamId.${containerExtension}",
    group = category,
    title = name.orEmpty(),
    cover = streamIcon,
    playlistUrl = playlistUrl
)

fun XtreamSerial.toStream(
    address: String,
    username: String,
    password: String,
    playlistUrl: String,
    category: String,
    containerExtension: String
): Stream = Stream(
    url = "$address/series/$username/$password/$seriesId.$containerExtension",
    group = category,
    title = name.orEmpty(),
    cover = cover,
    playlistUrl = playlistUrl,
)

@Serializable
data class XtreamCategory(
    @SerialName("category_id")
    val categoryId: Int?,
    @SerialName("category_name")
    val categoryName: String?,
    @SerialName("parent_id")
    val parentId: Int?
)
