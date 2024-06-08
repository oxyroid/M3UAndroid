package com.m3u.data.parser.xtream

import com.m3u.data.database.model.Channel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface XtreamData

@Serializable
data class XtreamLive(
//    @SerialName("added")
//    val added: String?,
    @SerialName("category_id")
    val categoryId: Int?,
//    @SerialName("custom_sid")
//    val customSid: String?,
//    @SerialName("direct_source")
//    val directSource: String?,
    @SerialName("epg_channel_id")
    val epgChannelId: String?,
    @SerialName("name")
    val name: String?,
//    @SerialName("num")
//    val num: Int?,
    @SerialName("stream_icon")
    val streamIcon: String?,
    @SerialName("stream_id")
    val streamId: Int?,
    @SerialName("stream_type")
    val streamType: String?,
//    @SerialName("tv_archive")
//    val tvArchive: Int?,
//    @SerialName("tv_archive_duration")
//    val tvArchiveDuration: Int?,
) : XtreamData

@Serializable
data class XtreamVod(
//    @SerialName("added")
//    val added: String? = null,
    @SerialName("category_id")
    val categoryId: Int? = null,
    @SerialName("container_extension")
    val containerExtension: String? = null,
//    @SerialName("custom_sid")
//    val customSid: String? = null,
//    @SerialName("direct_source")
//    val directSource: String? = null,
    @SerialName("name")
    val name: String? = null,
//    @SerialName("num")
//    val num: String? = null,
//    @SerialName("rating")
//    val rating: String? = null,
//    @SerialName("rating_5based")
//    val rating5based: String? = null,
    @SerialName("stream_icon")
    val streamIcon: String? = null,
    @SerialName("stream_id")
    val streamId: Int? = null,
    @SerialName("stream_type")
    val streamType: String? = null
) : XtreamData

@Serializable
data class XtreamSerial(
//    @SerialName("cast")
//    val cast: String? = null,
    @SerialName("category_id")
    val categoryId: Int? = null,
    @SerialName("cover")
    val cover: String? = null,
//    @SerialName("director")
//    val director: String? = null,
//    @SerialName("episode_run_time")
//    val episodeRunTime: String? = null,
//    @SerialName("genre")
//    val genre: String? = null,
//    @SerialName("last_modified")
//    val lastModified: String? = null,
    @SerialName("name")
    val name: String? = null,
//    @SerialName("num")
//    val num: String? = null,
//    @SerialName("plot")
//    val plot: String? = null,
//    @SerialName("rating")
//    val rating: String? = null,
//    @SerialName("rating_5based")
//    val rating5based: String? = null,
//    @SerialName("releaseDate")
//    val releaseDate: String? = null,
    @SerialName("series_id")
    val seriesId: Int? = null,
//    @SerialName("youtube_trailer")
//    val youtubeTrailer: String? = null
) : XtreamData

fun XtreamLive.toChannel(
    basicUrl: String,
    username: String,
    password: String,
    playlistUrl: String,
    category: String,
    // one of "allowed_output_formats"
    containerExtension: String
): Channel = Channel(
    url = "$basicUrl/live/$username/$password/$streamId.$containerExtension",
    category = category,
    title = name.orEmpty(),
    cover = streamIcon,
    playlistUrl = playlistUrl,
    originalId = epgChannelId
)

fun XtreamVod.toChannel(
    basicUrl: String,
    username: String,
    password: String,
    playlistUrl: String,
    category: String
): Channel = Channel(
    url = "$basicUrl/movie/$username/$password/$streamId.${containerExtension}",
    category = category,
    title = name.orEmpty(),
    cover = streamIcon,
    playlistUrl = playlistUrl,
    originalId = streamId?.toString()
)

fun XtreamSerial.asChannel(
    basicUrl: String,
    username: String,
    password: String,
    playlistUrl: String,
    category: String
): Channel = Channel(
    url = "$basicUrl/series/$username/$password/$seriesId",
    category = category,
    title = name.orEmpty(),
    cover = cover,
    playlistUrl = playlistUrl,
    originalId = seriesId?.toString()
)
