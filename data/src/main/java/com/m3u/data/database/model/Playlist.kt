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
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.i18n.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Entity(tableName = "playlists")
@Immutable
@Serializable
@Keep
data class Playlist(
    @ColumnInfo(name = "title")
    val title: String,
    @PrimaryKey
    @ColumnInfo(name = "url")
    // subscribing url
    // it may contains the special query param
    // which is only used in this project in order to distinguish between different types.
    // for example, if the source is [DataSource.Xtream],
    // you can use [XtreamInput.decodeFromPlaylistUrl] get its real information include
    // basicUrl, username, password, type(the special query param) and etc.
    // and then in the xtream-parser we can parse the data only for special type streams.
    val url: String,
    // extra fields
    @ColumnInfo(name = "pinned_groups", defaultValue = "[]")
    val pinnedCategories: List<String> = emptyList(),
    @ColumnInfo(name = "hidden_groups", defaultValue = "[]")
    val hiddenCategories: List<String> = emptyList(),
    @ColumnInfo(name = "source", defaultValue = "0")
    @Serializable(with = DataSourceSerializer::class)
    val source: DataSource = DataSource.M3U,
    @ColumnInfo(name = "user_agent", defaultValue = "NULL")
    val userAgent: String? = null,
    @ColumnInfo(name = "epg_url", defaultValue = "NULL")
    val epgUrl: String? = null
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

    val type: String?
        get() = when (source) {
            DataSource.Xtream -> XtreamInput.decodeFromPlaylistUrl(url).type
            else -> null
        }

    val typeWithSource: String?
        get() {
            if (type == null) return null
            return "$source $type"
        }

    override fun like(another: Playlist): Boolean {
        return title == another.title && url == another.url
                && epgUrl == another.epgUrl && source == another.source
    }

    companion object {
        const val URL_IMPORTED = "imported"

        val SERIES_TYPES = arrayOf(
            DataSource.Xtream.TYPE_SERIES
        )
        val VOD_TYPES = arrayOf(
            DataSource.Xtream.TYPE_VOD
        )
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

@Keep
data class PlaylistWithCount(
    @Embedded
    val playlist: Playlist,
    @ColumnInfo("count")
    val count: Int
)

sealed class DataSource(
    @StringRes val resId: Int,
    val value: String,
    val supported: Boolean = false
) {
    object M3U : DataSource(R.string.feat_setting_data_source_m3u, "m3u", true)

    object Xtream : DataSource(R.string.feat_setting_data_source_xtream, "xtream", true) {
        const val TYPE_LIVE = "live"
        const val TYPE_VOD = "vod"
        const val TYPE_SERIES = "series"
    }

    object Emby : DataSource(R.string.feat_setting_data_source_emby, "emby")

    object Dropbox : DataSource(R.string.feat_setting_data_source_dropbox, "dropbox")

    object Aliyun : DataSource(R.string.feat_setting_data_source_aliyun, "aliyun")

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

        fun ofOrNull(value: String): DataSource? = runCatching { of(value) }.getOrNull()
    }
}

private object DataSourceSerializer : KSerializer<DataSource> {
    override fun deserialize(decoder: Decoder): DataSource {
        return DataSource.of(decoder.decodeString())
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "com.m3u.data.database.model.DataSource",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: DataSource) {
        encoder.encodeString(value.value)
    }
}

fun Playlist.copyXtreamSeries(series: Stream): Playlist = copy(
    title = series.title
)
