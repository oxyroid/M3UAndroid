package com.m3u.extension.runtime

import android.graphics.drawable.Drawable
import com.m3u.extension.api.analyzer.Analyzer
import com.m3u.extension.api.analyzer.HlsPropAnalyzer

data class Extension(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val hlsPropAnalyzer: HlsPropAnalyzer?,
) {
    val analyzers: List<Analyzer>
        get() = listOfNotNull(hlsPropAnalyzer)
}
