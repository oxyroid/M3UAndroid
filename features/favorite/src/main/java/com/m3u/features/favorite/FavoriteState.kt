package com.m3u.features.favorite

import com.m3u.core.architecture.configuration.Configuration
import com.m3u.data.database.entity.Live

typealias LiveDetails = Map<String, List<Live>>

data class FavoriteState(
    val details: LiveDetails = emptyMap(),
    val rowCount: Int = Configuration.DEFAULT_ROW_COUNT,
    val godMode: Boolean = Configuration.DEFAULT_GOD_MODE
)