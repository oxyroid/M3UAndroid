package com.m3u.data.database.model

import androidx.room.ColumnInfo

data class AdjacentChannels(
    @ColumnInfo("prev_id")
    val prevId: Int?,
    @ColumnInfo("next_id")
    val nextId: Int?
)