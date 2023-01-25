package com.m3u.features.live

import com.m3u.core.wrapper.Event
import com.m3u.data.entity.Live

data class LiveState(
    val live: Live? = null,
    val message: Event<String> = Event.Handled(),
)
