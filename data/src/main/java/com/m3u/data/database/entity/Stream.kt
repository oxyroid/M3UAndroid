package com.m3u.data.database.entity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.m3u.core.util.Likable

@Entity(tableName = "streams")
@Immutable
data class Stream(
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "group")
    val group: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "cover")
    val cover: String? = null,
    @ColumnInfo(name = "playlistUrl")
    val playlistUrl: String,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    // extra fields
    @ColumnInfo(name = "favourite")
    val favourite: Boolean = false,
    @ColumnInfo(name = "banned")
    val banned: Boolean = false,
    @ColumnInfo(name = "seen", defaultValue = "0")
    val seen: Long = 0L
) : Likable<Stream> {
    override infix fun like(another: Stream): Boolean =
        this.url == another.url && this.playlistUrl == another.playlistUrl && this.cover == another.cover
                && this.group == another.group && this.title == another.title
}

@Immutable
data class StreamHolder(
    val streams: List<Stream> = emptyList(),
    val floating: Stream? = null
)

@Composable
fun rememberStreamHolder(
    streams: List<Stream>,
    floating: Stream? = null
): StreamHolder {
    return remember(streams, floating) {
        StreamHolder(streams, floating)
    }
}