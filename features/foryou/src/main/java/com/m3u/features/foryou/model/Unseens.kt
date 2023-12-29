package com.m3u.features.foryou.model

import androidx.compose.runtime.Immutable
import com.m3u.data.database.entity.Stream

@Immutable
internal data class Unseens(
    val streams: List<Stream> = emptyList()
)
