package com.m3u.data.parser.epg

import com.m3u.data.database.model.Programme
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern

data class EpgProgramme(
    val channel: String,
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
        fun readEpochMilliseconds(time: String): Long = EPG_DATE_TIME_FORMATTER
            .parse(time)
            .toInstantUsingOffset()
            .toEpochMilliseconds()

        @OptIn(FormatStringsInDatetimeFormats::class)
        private val EPG_DATE_TIME_FORMATTER = DateTimeComponents.Format {
            byUnicodePattern("yyyyMMddHHmmss[ Z]")
        }
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
