package com.m3u.data.parser.epg

import com.m3u.data.database.model.Programme
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
        fun readEpochMilliseconds(time: String): Long {
            val normalized = time.trim()
            normalized.toLongOrNull()?.let { value ->
                return if (normalized.length <= EPOCH_SECONDS_LENGTH) value * 1000 else value
            }
            return EPG_DATE_TIME_PARSERS
                .firstNotNullOfOrNull { parser ->
                    runCatching { parser(normalized) }.getOrNull()
                }
                ?: ZonedDateTime
                    .parse(normalized, EPG_DATE_TIME_FORMATTER)
                    .toInstant()
                    .toEpochMilli()
        }

        private val EPG_DATE_TIME_FORMATTER = DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHHmmss")
            .optionalStart()
            .appendPattern(" Z")
            .optionalEnd()
            .toFormatter()
        private val EPG_DATE_TIME_PARSERS: List<(String) -> Long> = listOf(
            { value -> Instant.parse(value).toEpochMilli() },
            { value -> OffsetDateTime.parse(value).toInstant().toEpochMilli() },
            { value ->
                ZonedDateTime
                    .parse(value, COMPACT_DATE_TIME_OFFSET_FORMATTER)
                    .toInstant()
                    .toEpochMilli()
            },
            { value ->
                ZonedDateTime
                    .parse(value, SPACE_SEPARATED_DATE_TIME_OFFSET_FORMATTER)
                    .toInstant()
                    .toEpochMilli()
            },
            { value ->
                LocalDateTime
                    .parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            },
            { value ->
                LocalDateTime
                    .parse(value, SPACE_SEPARATED_DATE_TIME_FORMATTER)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        )
        private val SPACE_SEPARATED_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val COMPACT_DATE_TIME_OFFSET_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ")
        private val SPACE_SEPARATED_DATE_TIME_OFFSET_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
        private const val EPOCH_SECONDS_LENGTH = 10
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
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .map { channelId -> programme.copy(channelId = channelId) }
}
