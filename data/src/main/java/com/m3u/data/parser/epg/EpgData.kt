package com.m3u.data.parser.epg

import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.Programme
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
    val start: String? = null,
    val stop: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val icon: String? = null,
    val categories: List<String>
)

fun EpgProgramme.toProgramme(
    streamId: Int
): Programme = Programme(
    streamId = streamId,
    start = start?.let {
        LocalDateTime
            .parse(it, EPG_DATE_TIME_FORMATTER)
            .toKotlinLocalDateTime()
            .run { hour * 60L * 60L * 1000L + minute * 60L * 1000L + second * 1000L }
    } ?: 0L,
    end = stop?.let {
        LocalDateTime
            .parse(it, EPG_DATE_TIME_FORMATTER)
            .toKotlinLocalDateTime()
            .run { hour * 60L * 60L * 1000L + minute * 60L * 1000L + second * 1000L }
    } ?: 0L,
    title = title.orEmpty(),
    description = desc.orEmpty(),
    icon = icon,
    categories = categories
)

private val EPG_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
