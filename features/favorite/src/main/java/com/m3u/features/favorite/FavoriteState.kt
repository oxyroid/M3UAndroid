package com.m3u.features.favorite

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.data.database.entity.Live

typealias LiveDetails = Map<String, List<Live>>

data class FavoriteState(
    val details: LiveDetails = emptyMap(),
    val configuration: Configuration
) {

    var rowCount: Int by configuration.rowCount
    var godMode: Boolean by configuration.godMode
    var noPictureMode: Boolean by configuration.noPictureMode
}