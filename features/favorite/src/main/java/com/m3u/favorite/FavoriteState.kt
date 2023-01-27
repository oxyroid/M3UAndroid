package com.m3u.favorite

import android.os.Parcelable
import com.m3u.core.wrapper.Event
import com.m3u.favorite.vo.LiveWithTitle
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class FavoriteState(
    val lives: List<LiveWithTitle> = emptyList(),
    val message: @RawValue Event<String> = Event.Handled()
) : Parcelable
