package com.m3u.data.parser.epg

import com.m3u.data.database.model.Programme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder

data class EpgProgramme(
    val channel: String,
    val channelAliases: List<String> = emptyList(),
    // use [readEpochMilliseconds]
    val start: String? = null,
    // use [readEpochMilliseconds]
    val stop: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val icon: String? = null,
    val categories: List<String>
) {
    companion object {
        fun readEpochMilliseconds(time: String): Long = ZonedDateTime
            .parse(time, EPG_DATE_TIME_FORMATTER)
            .toInstant()
            .toEpochMilli()

        private val EPG_DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHHmmss")
            .optionalStart()
            .appendPattern(" Z")
            .optionalEnd()
            .toFormatter()
    }
}

fun EpgProgramme.toProgramme(
    epgUrl: String
): Programme = Programme(
    epgUrl = epgUrl,
    start = start?.let {
        EpgProgramme.readEpochMilliseconds(it)
    } ?: 0L,
    end = stop?.let {
        EpgProgramme.readEpochMilliseconds(it)
    } ?: 0L,
    title = title.orEmpty(),
    description = desc.orEmpty(),
    icon = icon,
    categories = categories,
    channelId = channel
)

fun EpgProgramme.toProgrammes(
    epgUrl: String
): List<Programme> {
    val programme = toProgramme(epgUrl)
    return listOf(channel)
        .plus(channelAliases)
        .filter { it.isNotBlank() }
        .distinct()
        .map { channelId -> programme.copy(channelId = channelId) }
}
