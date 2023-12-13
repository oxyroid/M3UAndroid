package com.m3u.features.favorite

import com.m3u.data.database.entity.Stream

typealias StreamDetails = Map<String, List<Stream>>

data class FavoriteState(
    val details: StreamDetails = emptyMap(),
)