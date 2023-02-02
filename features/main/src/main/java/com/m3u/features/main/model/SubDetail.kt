package com.m3u.features.main.model

import com.m3u.data.entity.Subscription

data class SubDetail(
    val subscription: Subscription,
    val count: Int
)

internal fun Subscription.toDetail(
    count: Int = 0
): SubDetail {
    return SubDetail(
        this, count
    )
}