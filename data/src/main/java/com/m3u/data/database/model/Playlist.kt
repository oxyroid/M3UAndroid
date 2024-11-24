package com.m3u.data.database.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.m3u.annotation.Exclude
import com.m3u.annotation.Likable
import com.m3u.core.util.basic.startsWithAny
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.i18n.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@Entity(tableName = "playlists")
@Immutable
@Serializable
@Likable
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
    // and then in the xtream-parser we can parse the data only for special type channels.
    val url: String,
    // extra fields
    @ColumnInfo(name = "pinned_groups", defaultValue = "[]")
    @Exclude
    val pinnedCategories: List<String> = emptyList(),
    @ColumnInfo(name = "hidden_groups", defaultValue = "[]")
    @Exclude
    val hiddenCategories: List<String> = emptyList(),
    @ColumnInfo(name = "source", defaultValue = "0")
    @Serializable(with = DataSourceSerializer::class)
    val source: DataSource = DataSource.M3U,
    @ColumnInfo(name = "user_agent", defaultValue = "NULL")
    @Exclude
    val userAgent: String? = null,
    // epg playlist urls
    @ColumnInfo(name = "epg_urls", defaultValue = "[]")
    @Exclude
    val epgUrls: List<String> = emptyList(),
    @ColumnInfo(name = "auto_refresh_programmes", defaultValue = "0")
    @Exclude
    val autoRefreshProgrammes: Boolean = false
) {
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

val Playlist.isSeries: Boolean get() = type in Playlist.SERIES_TYPES
val Playlist.isVod: Boolean get() = type in Playlist.VOD_TYPES

val Playlist.fromLocal: Boolean
    get() {
        if (source != DataSource.M3U) return false
        return url == Playlist.URL_IMPORTED || url.startsWithAny(
            "file://",
            "content://",
            ignoreCase = true
        )
    }

val Playlist.type: String?
    get() = when (source) {
        DataSource.Xtream -> XtreamInput.decodeFromPlaylistUrl(url).type
        else -> null
    }

fun Playlist.epgUrlsOrXtreamXmlUrl(): List<String> = when (source) {
    DataSource.Xtream -> {
        when (type) {
            DataSource.Xtream.TYPE_LIVE -> {
                val input = XtreamInput.decodeFromPlaylistUrl(url)
                val epgUrl = XtreamParser.createXmlUrl(
                    basicUrl = input.basicUrl,
                    username = input.username,
                    password = input.password
                )
                listOf(epgUrl)
            }

            else -> emptyList()
        }
    }

    else -> epgUrls
}

fun Playlist.copyXtreamSeries(series: Channel): Playlist = copy(
    title = series.title
)

@Immutable
@Serializable
sealed class DataSource(
    @StringRes
    @Transient
    val resId: Int = 0,
    @Transient
    val value: String = "",
    @Transient
    val supported: Boolean = false
) {
    object M3U : DataSource(R.string.feat_setting_data_source_m3u, "m3u", true)

    // special playlist type.
    // not like other playlist types, it maps to programmes but not channels.
    // so epg playlists should not be displayed in foryou page.
    // m3u playlist can refer epg playlist ids.
    // xtream playlist need not save or refer epg playlists.
    object EPG : DataSource(R.string.feat_setting_data_source_epg, "epg", true)

    object Xtream : DataSource(R.string.feat_setting_data_source_xtream, "xtream", true) {
        const val TYPE_LIVE = "live"
        const val TYPE_VOD = "vod"
        const val TYPE_SERIES = "series"
    }

    object Emby : DataSource(R.string.feat_setting_data_source_emby, "emby")

    object Dropbox : DataSource(R.string.feat_setting_data_source_dropbox, "dropbox")

    @Serializable
    data class Ext(
        val label: String,
        val pkgName: String,
        val classPath: String
    ) : DataSource(0, pkgName, true)

    override fun toString(): String = value

    companion object {
        fun of(value: String): DataSource = when (value) {
            "m3u" -> M3U
            "epg" -> EPG
            "xtream" -> Xtream
            "emby" -> Emby
            "dropbox" -> Dropbox
            else -> throw UnsupportedOperationException()
        }

        fun ofOrNull(value: String): DataSource? = runCatching { of(value) }.getOrNull()
    }
}

data class PlaylistWithChannels(
    @Embedded
    val playlist: Playlist,
    @Relation(
        parentColumn = "url",
        entityColumn = "playlist_url"
    )
    val channels: List<Channel>
)

@Immutable
data class PlaylistWithCount(
    @Embedded
    val playlist: Playlist,
    @ColumnInfo("count")
    val count: Int
)

internal object DataSourceSerializer : KSerializer<DataSource> {
    override fun deserialize(decoder: Decoder): DataSource = DataSource
        .ofOrNull(decoder.decodeString()) ?: decoder.decodeStructure(descriptor) {
        DataSource.Ext(
            label = decodeStringElement(descriptor, 0),
            pkgName = decodeStringElement(descriptor, 1),
            classPath = decodeStringElement(descriptor, 2)
        )
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "com.m3u.data.database.model.DataSource",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: DataSource) {
        when (value) {
            is DataSource.Ext -> encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.label)
                encodeStringElement(descriptor, 1, value.pkgName)
                encodeStringElement(descriptor, 2, value.classPath)
            }

            else -> encoder.encodeString(value.value)
        }
    }
}
