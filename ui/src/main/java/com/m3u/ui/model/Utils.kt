package com.m3u.ui.model

import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf

interface Utils {
    fun enterPipMode(source: Rect)
    fun setTitle(title: String = "")
    fun setActions(actions: List<AppAction> = emptyList())
    fun hideSystemUI()
    fun showSystemUI()
}

val LocalUtils = staticCompositionLocalOf<Utils> { error("No utils provided.") }