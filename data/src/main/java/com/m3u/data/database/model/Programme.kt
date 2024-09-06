package com.m3u.data.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Entity(tableName = "programmes")
@Immutable
// epg programme
data class Programme(
    // m3u tvg-id
    @ColumnInfo(name = "relation_id")
    val channelId: String,
    // playlistUrl in Playlist which source is epg
    // for more details, see [DataSource.EPG].
    @ColumnInfo(name = "epg_url", index = true)
    val epgUrl: String,
    @ColumnInfo(name = "start")
    val start: Long,
    @ColumnInfo(name = "end")
    val end: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "icon")
    val icon: String? = null,
    @ColumnInfo(name = "categories")
    val categories: List<String>,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0
)

data class ProgrammeRange(
    @ColumnInfo("start_edge")
    val start: Long,
    @ColumnInfo("end_edge")
    val end: Long
) {
    fun spread(spread: Spread): ProgrammeRange =
        when (spread) {
            is Spread.Absolute -> {
                val duration = spread.duration - (end - start).toDuration(DurationUnit.MILLISECONDS)
                when {
                    !spread.increaseOnly || duration.isPositive() -> {
                        ProgrammeRange(
                            start,
                            (Instant.fromEpochMilliseconds(end) + duration).toEpochMilliseconds()
                        )
                    }

                    else -> this
                }
            }

            is Spread.Increase -> {
                ProgrammeRange(
                    Instant.fromEpochMilliseconds(start).minus(spread.prev).toEpochMilliseconds(),
                    Instant.fromEpochMilliseconds(end).plus(spread.future).toEpochMilliseconds()
                )
            }
        }

    operator fun plus(duration: Duration): ProgrammeRange = ProgrammeRange(
        Instant.fromEpochMilliseconds(start).plus(duration).toEpochMilliseconds(),
        Instant.fromEpochMilliseconds(end).plus(duration).toEpochMilliseconds()
    )

    sealed interface Spread {
        data class Absolute(
            val duration: Duration = 12.hours,
            val increaseOnly: Boolean = true
        ) : Spread

        data class Increase(val prev: Duration = 4.hours, val future: Duration = 8.hours) : Spread
    }

    companion object {
        const val HOUR_LENGTH = 3600000L
    }
}