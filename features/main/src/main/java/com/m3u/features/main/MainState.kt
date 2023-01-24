package com.m3u.features.main

import com.m3u.features.main.vo.SubscriptionVO

sealed interface MainState {
    object Loading: MainState
    data class Success(
        val subscriptions: List<SubscriptionVO> = emptyList(),

    ): MainState
}