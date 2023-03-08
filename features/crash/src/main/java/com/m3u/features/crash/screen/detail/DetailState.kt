package com.m3u.features.crash.screen.detail

import androidx.compose.runtime.Stable
import java.io.File

@Stable
data class DetailState(
    val file: File? = null
)
