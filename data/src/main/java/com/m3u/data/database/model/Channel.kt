package com.m3u.data.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.m3u.annotation.Exclude
import com.m3u.annotation.Likable
import com.m3u.data.parser.xtream.XtreamEpisodeInfo
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
@Likable
data class Channel(
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
    @ColumnInfo(name = "playlist_url", index = true)
    val playlistUrl: String,
    @ColumnInfo(name = "license_type", defaultValue = "NULL")
    val licenseType: String? = null,
    @ColumnInfo(name = "license_key", defaultValue = "NULL")
    val licenseKey: String? = null,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    @Exclude
    val id: Int = 0,
    // extra fields
    @ColumnInfo(name = "favourite", index = true)
    @Exclude
    val favourite: Boolean = false,
    @ColumnInfo(name = "hidden", defaultValue = "0")
    @Exclude
    val hidden: Boolean = false,
    @ColumnInfo(name = "seen", defaultValue = "0")
    @Exclude
    val seen: Long = 0L,
    @ColumnInfo(name = "media_kind", defaultValue = "'unknown'")
    @Exclude
    val mediaKind: String = MediaKinds.UNKNOWN,
    @ColumnInfo(name = "playable", defaultValue = "1")
    @Exclude
    val playable: Boolean = true,
    @ColumnInfo(name = "browsable", defaultValue = "0")
    @Exclude
    val browsable: Boolean = false,
    @ColumnInfo(name = "relation_id", defaultValue = "NULL")
    @Exclude
    /**
     * if it is from m3u, corresponds to 'channel-id' field in the EPG xml file.
     * kodi adaptive: it may be 'tvg-id', if missing from the M3U file,
     * the addon will use the 'tvg-name' tag to map the channel to the EPG.
     * https://kodi.wiki/view/Add-on:PVR_IPTV_Simple_Client#Usage
     *
     * if it is xtream live, it may be epgChannelId.
     * if it is xtream vod, it may be streamId.
     * if it is xtream series, it may be seriesId.
     */
    val relationId: String? = null,
    @ColumnInfo(name = "subtitle", defaultValue = "NULL")
    val subtitle: String? = null,
    @ColumnInfo(name = "overview", defaultValue = "NULL")
    val overview: String? = null,
    @ColumnInfo(name = "production_year", defaultValue = "NULL")
    val productionYear: Int? = null,
) {
    init {
        require(mediaKind.matches(MEDIA_KIND_PATTERN)) {
            "Media kind must be a lowercase identifier"
        }
        require(playable || browsable) {
            "A media item must be playable, browsable, or both"
        }
        require(productionYear == null || productionYear in 1..9_999) {
            "Production year is out of range"
        }
    }

    companion object {
        const val URL_DYNAMIC = "dynamic"
        const val LICENSE_TYPE_WIDEVINE = "com.widevine.alpha"
        const val LICENSE_TYPE_CLEAR_KEY = "clearkey"
        const val LICENSE_TYPE_CLEAR_KEY_2 = "org.w3.clearkey"
        const val LICENSE_TYPE_PLAY_READY = "com.microsoft.playready"
        private val MEDIA_KIND_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
    }
}

object MediaKinds {
    const val UNKNOWN = "unknown"
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val EPISODE = "episode"
}

enum class MediaOpenAction {
    PLAY,
    BROWSE,
    UNSUPPORTED,
}

fun Channel.openAction(playlist: Playlist?): MediaOpenAction = when {
    browsable -> MediaOpenAction.BROWSE
    playable -> {
        if (playlist?.isSeries == true) MediaOpenAction.BROWSE else MediaOpenAction.PLAY
    }
    else -> MediaOpenAction.UNSUPPORTED
}

fun Channel.stablePlaybackKey(): String =
    if (url != Channel.URL_DYNAMIC) {
        url
    } else {
        "dynamic:$playlistUrl:${relationId ?: id}"
    }

fun Channel.copyXtreamEpisode(episode: XtreamEpisodeInfo): Channel {
    val url = Url(url)
    val newUrl = URLBuilder(url)
        .apply { path(*url.rawSegments.dropLast(1).toTypedArray()) }
        .appendPathSegments("${episode.id}.${episode.containerExtension}")
        .build()
    return copy(
        url = newUrl.toString(),
        title = episode.title.orEmpty()
    )
}
