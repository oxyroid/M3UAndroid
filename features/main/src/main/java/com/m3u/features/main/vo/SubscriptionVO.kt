package com.m3u.features.main.vo

import androidx.compose.runtime.Immutable
import com.m3u.data.entity.Subscription

@Immutable
data class SubscriptionVO(
    val label: String,
    val count: Int = 0,
    val preview: String = "",
    val lastUpdate: String = ""
)

fun Subscription.toViewObject(): SubscriptionVO {
    return SubscriptionVO(
        label = title
    )
}