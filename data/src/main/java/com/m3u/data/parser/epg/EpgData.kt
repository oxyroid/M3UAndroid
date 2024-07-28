package com.m3u.data.parser.epg

import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.Programme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatterBuilder

@Immutable
data class EpgData(
    val channels: List<EpgChannel> = emptyList(),
    val programmes: List<EpgProgramme> = emptyList()
)

data class EpgChannel(
    val id: String,
    val displayName: String? = null,
    val icon: String? = null,
    val url: String? = null
)

data class EpgProgramme(
    val channel: String,
    // use [readEpochMilliseconds]
    val start: String? = null,
    // use [readEpochMilliseconds]
    val stop: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val icon: String? = null,
    val isNew: Boolean = false,
    val isLive: Boolean = false,
    val previouslyShownStart: String? = null,
    val categories: List<String>
) {
    companion object {
        fun readEpochMilliseconds(time: String): Long = LocalDateTime
            .parse(time, EPG_DATE_TIME_FORMATTER)
            .toKotlinLocalDateTime()
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds()

        private val EPG_DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHHmmss")
            .optionalStart()
            .appendPattern(" Z")
            .optionalEnd()
            .toFormatter()
//            .withZone(ZoneId.of("GMT"))
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
    isNew = isNew,
    isLive = isLive,
    previouslyShownStart = previouslyShownStart,
    categories = categories,
    channelId = channel
)
