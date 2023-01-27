package com.m3u.data.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "lives")
@Parcelize
data class Live(
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "group")
    val group: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "cover")
    val cover: String? = null,
    @ColumnInfo(name = "subscriptionUrl")
    val subscriptionUrl: String,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    // extra fields
    @ColumnInfo(name = "favourite")
    val favourite: Boolean = false
) : Parcelable
