package com.m3u.features.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.features.main.model.FeedDetail

data class MainState(
    val loading: Boolean = false,
    private val configuration: Configuration,
    val feeds: List<FeedDetail> = emptyList(),
) {
    var godMode: Boolean by configuration.godMode
    var rowCount: Int by configuration.rowCount
}