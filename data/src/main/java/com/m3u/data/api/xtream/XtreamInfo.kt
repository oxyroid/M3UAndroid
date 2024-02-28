package com.m3u.data.api.xtream

import com.m3u.data.database.model.Stream
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
