package com.m3u.extension.runtime

import com.m3u.extension.api.runner.Runner

data class Extension(
    val packageName: String,
    val runners: List<Runner> = emptyList()
)
