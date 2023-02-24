package com.m3u.features.live

import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.entity.Live

data class LiveState(
    val live: Live? = null,
    val recording: Boolean = false,
    val message: Event<String> = handledEvent(),
)
