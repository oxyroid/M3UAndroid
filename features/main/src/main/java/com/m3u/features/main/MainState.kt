package com.m3u.features.main

import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.features.main.model.FeedDetail

data class MainState(
    val loading: Boolean = false,
    val feeds: List<FeedDetail> = emptyList(),
    val message: Event<String> = handledEvent(),
)