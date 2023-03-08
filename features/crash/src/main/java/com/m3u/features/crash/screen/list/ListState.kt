package com.m3u.features.crash.screen.list

import androidx.compose.runtime.Stable
import java.io.File

@Stable
data class ListState(
    val logs: List<File> = emptyList()
)
