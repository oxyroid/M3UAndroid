package com.m3u.features.main

import android.os.Parcelable
import com.m3u.core.wrapper.Event
import com.m3u.features.main.model.SubDetail
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue


@Parcelize
data class MainState(
    val loading: Boolean = false,
    val details: List<SubDetail> = emptyList(),
    val message: @RawValue Event<String> = Event.Handled(),
) : Parcelable