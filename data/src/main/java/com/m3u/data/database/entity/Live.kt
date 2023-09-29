package com.m3u.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.m3u.core.util.Likable

// called channel in user interface
@Entity(tableName = "lives")
data class Live(
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "group")
    val group: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "cover")
    val cover: String? = null,
    @ColumnInfo(name = "feedUrl")
    val feedUrl: String,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    // extra fields
    @ColumnInfo(name = "favourite")
    val favourite: Boolean = false,
    @ColumnInfo(name = "banned")
    val banned: Boolean = false,
) : Likable<Live> {
    override infix fun like(another: Live): Boolean =
        this.url == another.url && this.feedUrl == another.feedUrl && this.cover == another.cover
                && this.group == another.group && this.title == another.title
}
