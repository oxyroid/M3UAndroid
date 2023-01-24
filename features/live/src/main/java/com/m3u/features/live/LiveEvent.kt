package com.m3u.features.live

import com.m3u.data.entity.Live

sealed interface LiveEvent {
    data class Init(val live: Live) : LiveEvent
}