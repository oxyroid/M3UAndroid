package com.m3u.extension.runtime

import android.graphics.drawable.Drawable
import com.m3u.extension.api.analyzer.Analyzer
import com.m3u.extension.api.analyzer.HlsPropAnalyzer
import com.m3u.extension.api.workflow.Workflow

data class Extension(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val hlsPropAnalyzer: HlsPropAnalyzer?,
    val workflows: List<Workflow>
) {
    val analyzers: List<Analyzer>
        get() = listOfNotNull(hlsPropAnalyzer)
}
