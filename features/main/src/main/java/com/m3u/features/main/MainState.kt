package com.m3u.features.main

import android.os.Parcelable
import com.m3u.core.wrapper.Event
import com.m3u.features.main.vo.SubscriptionDetail
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue


@Parcelize
data class MainState(
    val loading: Boolean = false,
    val subscriptions: List<SubscriptionDetail> = emptyList(),
    val message: @RawValue Event<String> = Event.Handled(),
) : Parcelable