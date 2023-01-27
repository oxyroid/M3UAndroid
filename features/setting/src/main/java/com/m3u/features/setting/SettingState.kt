package com.m3u.features.setting

import android.os.Parcelable
import com.m3u.core.wrapper.Event
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class SettingState(
    val adding: Boolean = false,
    val title: String = "",
    val url: String = "",
    val message: @RawValue Event<String> = Event.Handled(),
    val version: String = ""
) : Parcelable