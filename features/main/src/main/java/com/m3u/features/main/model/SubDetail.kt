package com.m3u.features.main.model

import android.os.Parcelable
import com.m3u.data.entity.Subscription
import kotlinx.parcelize.Parcelize

@Parcelize
data class SubDetail(
    val subscription: Subscription,
    val count: Int
) : Parcelable

internal fun Subscription.toDetail(
    count: Int = 0
): SubDetail {
    return SubDetail(
        this, count
    )
}