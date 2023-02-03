package com.m3u.features.subscription

import com.m3u.core.wrapper.Event
import com.m3u.data.entity.Live

data class SubscriptionState(
    val url: String = "",
    val lives: List<Live> = emptyList(),
    val syncing: Boolean = false,
    val message: Event<String> = Event.Handled()
)
