package com.m3u.data.parser.xtream

import com.m3u.data.database.model.Channel

typealias XtreamData = dev.oxyroid.parser.xtream.XtreamData
typealias XtreamLive = dev.oxyroid.parser.xtream.XtreamLive
typealias XtreamVod = dev.oxyroid.parser.xtream.XtreamVod
typealias XtreamSerial = dev.oxyroid.parser.xtream.XtreamSerial

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
    relationId = epgChannelId
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
    relationId = streamId?.toString()
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
    relationId = seriesId?.toString()
)
