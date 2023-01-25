package com.m3u.features.main

import com.m3u.core.wrapper.Event
import com.m3u.features.main.vo.SubscriptionState

data class MainState(
    val loading: Boolean = false,
    val subscriptions: List<SubscriptionState> = emptyList(),
    val message: Event<String> = Event.Handled(),
)