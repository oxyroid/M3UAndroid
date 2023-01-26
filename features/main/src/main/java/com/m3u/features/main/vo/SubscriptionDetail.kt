package com.m3u.features.main.vo

import android.os.Parcelable
import com.m3u.data.entity.Subscription
import kotlinx.parcelize.Parcelize


@Parcelize
data class SubscriptionDetail(
    val subscription: Subscription,
    val number: Int
) : Parcelable