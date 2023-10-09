package com.m3u.features.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.architecture.configuration.Configuration

data class MainState(
    val loading: Boolean = false,
    private val configuration: Configuration,
) {
    var godMode: Boolean by configuration.godMode
    var rowCount: Int by configuration.rowCount
}