package com.m3u.favorite.vo

import android.os.Parcelable
import com.m3u.data.entity.Live
import kotlinx.parcelize.Parcelize

data class LiveDetail(
    val live: Live,
    val title: String
) : Parcelable