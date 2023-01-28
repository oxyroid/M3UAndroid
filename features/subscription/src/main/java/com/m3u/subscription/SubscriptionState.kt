package com.m3u.subscription

import android.os.Parcelable
import com.m3u.core.wrapper.Event
import com.m3u.data.entity.Live
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class SubscriptionState(
    val url: String = "",
    val lives: List<Live> = emptyList(),
    val syncing: Boolean = false,
    val message: @RawValue Event<String> = Event.Handled()
) : Parcelable
