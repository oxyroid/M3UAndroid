package com.m3u.data.database.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "programmes")
@Immutable
@Keep
// epg programme
data class Programme(
    // m3u tvg-id
    @ColumnInfo(name = "channel_id")
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
    fun isEmpty(): Boolean = end - start <= 0
    fun count(unit: Long = 1L): Int = (end - start).floorDiv(unit).toInt()

    companion object {
        const val HOUR_LENGTH = 3600000L
    }
}