package com.m3u.data.util

import androidx.room.TypeConverter
import com.m3u.data.entity.LiveState

class LiveStateConverters {
    @TypeConverter
    fun boolToState(value: Boolean): LiveState = when (value) {
        true -> LiveState.Online
        false -> LiveState.Offline
    }

    @TypeConverter
    fun stateToBool(state: LiveState): Boolean = when (state) {
        LiveState.Online -> true
        LiveState.Offline -> false
    }
}