package com.m3u.subscription

import com.m3u.data.entity.Live

data class SubscriptionState(
    val title: String = "",
    val lives: List<Live> = emptyList(),
)
