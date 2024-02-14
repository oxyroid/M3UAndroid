package com.m3u.data.database.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.m3u.core.util.Likable
import kotlinx.serialization.Serializable

@Entity(tableName = "streams")
@Immutable
@Serializable
@Keep
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
    @ColumnInfo(name = "license_type", defaultValue = "NULL")
    val licenseType: String? = null,
    @ColumnInfo(name = "license_key", defaultValue = "NULL")
    val licenseKey: String? = null,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    // extra fields
    @ColumnInfo(name = "favourite")
    val favourite: Boolean = false,
    @ColumnInfo(name = "hidden", defaultValue = "0")
    val hidden: Boolean = false,
    @ColumnInfo(name = "seen", defaultValue = "0")
    val seen: Long = 0L,
) : Likable<Stream> {
    override infix fun like(another: Stream): Boolean =
        this.url == another.url && this.playlistUrl == another.playlistUrl && this.cover == another.cover
                && this.group == another.group && this.title == another.title && this.licenseType == another.licenseType
                && this.licenseKey == another.licenseKey

    companion object {
        const val LICENSE_TYPE_WIDEVINE = "com.widevine.alpha"
        const val LICENSE_TYPE_CLEAR_KEY = "clearkey"
        const val LICENSE_TYPE_PLAY_READY = "com.microsoft.playready"
    }
}
