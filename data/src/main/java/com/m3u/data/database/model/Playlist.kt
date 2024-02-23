package com.m3u.data.database.model

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.m3u.core.util.Likable
import com.m3u.core.util.basic.startsWithAny
import com.m3u.i18n.R
import kotlinx.serialization.Serializable

@Entity(tableName = "playlists")
@Immutable
@Serializable
@Keep
data class Playlist(
    @ColumnInfo(name = "title")
    val title: String,
    @PrimaryKey
    @ColumnInfo(name = "url")
    val url: String,
    // extra fields
    @ColumnInfo(name = "pinned_groups", defaultValue = "[]")
    val pinnedGroups: List<String> = emptyList(),
    @ColumnInfo(name = "source", defaultValue = "0")
    val source: DataSource = DataSource.M3U
) : Likable<Playlist> {
    val fromLocal: Boolean
        get() {
            if (source != DataSource.M3U) return false
            return url == URL_IMPORTED || url.startsWithAny(
                "file://",
                "content://",
                ignoreCase = true
            )
        }

    override fun like(another: Playlist): Boolean {
        return title == another.title && url == another.url
    }

    companion object {
        const val URL_IMPORTED = "imported"
    }
}

data class PlaylistWithStreams(
    @Embedded
    val playlist: Playlist,
    @Relation(
        parentColumn = "url",
        entityColumn = "playlistUrl"
    )
    val streams: List<Stream>
)

enum class DataSource(
    @StringRes val resId: Int,
    val value: String,
    val supported: Boolean = false
) {
    M3U(R.string.feat_setting_data_source_m3u, "m3u", true),
    Xtream(R.string.feat_setting_data_source_xtream, "xtream", true),
    Emby(R.string.feat_setting_data_source_emby, "emby"),
    Dropbox(R.string.feat_setting_data_source_dropbox, "dropbox"),
    Aliyun(R.string.feat_setting_data_source_aliyun, "aliyun");

    override fun toString(): String = value

    companion object {
        fun of(value: String): DataSource = when (value) {
            "m3u" -> M3U
            "xtream" -> Xtream
            "emby" -> Emby
            "dropbox" -> Dropbox
            "aliyun" -> Aliyun
            else -> throw UnsupportedOperationException()
        }

        fun ofOrNull(value: String): DataSource? =
            runCatching { of(value) }.getOrNull()
    }
}

