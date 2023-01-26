package com.m3u.features.live

import android.os.Parcelable
import com.m3u.core.wrapper.Event
import com.m3u.data.entity.Live
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class LiveState(
    val live: Live? = null,
    val message: @RawValue Event<String> = Event.Handled(),
): Parcelable
