package com.m3u.features.main

import com.m3u.core.wrapper.Event
import com.m3u.features.main.vo.SubscriptionDetail

data class MainState(
    val loading: Boolean = false,
    val subscriptions: List<SubscriptionDetail> = emptyList(),
    val message: Event<String> = Event.Handled(),
)