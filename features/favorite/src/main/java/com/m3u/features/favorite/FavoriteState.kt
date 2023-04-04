package com.m3u.features.favorite

import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.entity.Live

typealias LiveDetails = Map<String, List<Live>>

data class FavoriteState(
    val details: LiveDetails = emptyMap(),
    val message: Event<String> = handledEvent()
)