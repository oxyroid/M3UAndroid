package com.m3u.features.favorite

import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.entity.Live

data class FavoriteState(
    val lives: List<LiveDetail> = emptyList(),
    val message: Event<String> = handledEvent()
)

data class LiveDetail(
    val live: Live,
    val title: String
)