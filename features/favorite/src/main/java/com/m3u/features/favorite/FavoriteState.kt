package com.m3u.features.favorite

import com.m3u.data.database.entity.Live

typealias LiveDetails = Map<String, List<Live>>

data class FavoriteState(
    val details: LiveDetails = emptyMap(),
)