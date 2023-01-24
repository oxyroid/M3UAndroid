package com.m3u.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lives")
data class Live(
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "cover")
    val cover: String? = null,
    @ColumnInfo(name = "state")
    val state: LiveState = LiveState.Offline,
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0
)

enum class LiveState {
    Online, Offline
}