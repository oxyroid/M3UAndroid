package com.m3u.data.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.m3u.core.util.Likable
import com.m3u.data.parser.xtream.XtreamStreamInfo
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.path
import kotlinx.serialization.Serializable

@Entity(
    tableName = "streams"
)
@Immutable
@Serializable
data class Stream(
    @ColumnInfo(name = "url")
    // playable url
    // if its playlist type is in [Playlist.SERIES_TYPES]
    // you should load its episodes instead of playing it.
    val url: String,
    @ColumnInfo(name = "group")
    val category: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "cover")
    val cover: String? = null,
    @ColumnInfo(name = "playlistUrl", index = true)
    val playlistUrl: String,
    @ColumnInfo(name = "license_type", defaultValue = "NULL")
    val licenseType: String? = null,
    @ColumnInfo(name = "license_key", defaultValue = "NULL")
    val licenseKey: String? = null,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    // extra fields
    @ColumnInfo(name = "favourite", index = true)
    val favourite: Boolean = false,
    @ColumnInfo(name = "hidden", defaultValue = "0")
    val hidden: Boolean = false,
    @ColumnInfo(name = "seen", defaultValue = "0")
    val seen: Long = 0L,
    // only used to m3u stream
    @ColumnInfo(name = "channel_id", defaultValue = "NULL")
    // if it is from m3u, it may be "tvg-id"
    val channelId: String? = null
) : Likable<Stream> {
    override infix fun like(another: Stream): Boolean =
        this.url == another.url && this.playlistUrl == another.playlistUrl && this.cover == another.cover
                && this.category == another.category && this.title == another.title && this.licenseType == another.licenseType
                && this.licenseKey == another.licenseKey

    companion object {
        const val LICENSE_TYPE_WIDEVINE = "com.widevine.alpha"
        const val LICENSE_TYPE_CLEAR_KEY = "clearkey"
        const val LICENSE_TYPE_PLAY_READY = "com.microsoft.playready"
    }
}

fun Stream.copyXtreamEpisode(episode: XtreamStreamInfo.Episode): Stream {
    val url = Url(url)
    val newUrl = URLBuilder(url)
        .apply { path(*url.pathSegments.dropLast(1).toTypedArray()) }
        .appendPathSegments("${episode.id}.${episode.containerExtension}")
        .build()
    return copy(
        url = newUrl.toString(),
        title = episode.title.orEmpty()
    )
}