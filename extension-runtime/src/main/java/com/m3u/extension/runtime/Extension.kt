package com.m3u.extension.runtime

import android.graphics.drawable.Drawable
import com.m3u.extension.api.analyzer.Analyzer
import com.m3u.extension.api.runner.Runner

data class Extension(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val runners: List<Runner> = emptyList(),
    val analyzers: List<Analyzer> = emptyList()
)
