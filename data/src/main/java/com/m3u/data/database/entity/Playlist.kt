package com.m3u.data.database.entity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.m3u.core.util.basic.startsWithAny

@Entity(tableName = "playlists")
@Immutable
data class Playlist(
    @ColumnInfo(name = "title")
    val title: String,
    @PrimaryKey
    @ColumnInfo(name = "url")
    val url: String
) {
    val local: Boolean
        get() = url == URL_IMPORTED ||
                url.startsWithAny("file://", "content://")

    companion object {
        const val URL_IMPORTED = "imported"
    }
}

@Immutable
data class PlaylistHolder(
    val playlists: List<Playlist>
)

@Composable
fun rememberPlaylistHolder(playlists: List<Playlist>): PlaylistHolder {
    return remember(playlists) { PlaylistHolder(playlists) }
}
