package com.m3u.data.parser.epg

import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.Programme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val isNewTag: Boolean = false,
    val isLive: Boolean = false,
    val isLiveTag: Boolean = false,
    val previouslyShownStart: String? = null,
    val categories: List<String>
) {
    companion object {
        fun readEpochMilliseconds(time: String): Long = LocalDateTime
            .parse(time, EPG_DATE_TIME_FORMATTER)
            .toKotlinLocalDateTime()
            .toInstant(TimeZone.UTC)
            .toEpochMilliseconds()

        private val EPG_DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss Z")
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
    categories = categories,
    isNew = isNew,
    isNewTag = isNewTag,
    isLive = isLive,
    isLiveTag = isLiveTag,
    previouslyShownStart = previouslyShownStart,
    channelId = channel
)
