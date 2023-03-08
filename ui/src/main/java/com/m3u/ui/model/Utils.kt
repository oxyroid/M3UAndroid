package com.m3u.ui.model

import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf

interface Utils {
    fun enterPipMode(size: Rect)
    fun setTitle(title: String = "")
    fun setActions(actions: List<AppAction> = emptyList())
    fun hideSystemUI()
    fun showSystemUI()
}

val EmptyUtils = object : Utils {
    override fun enterPipMode(size: Rect) = error("Cannot enterPipMode")

    override fun setTitle(title: String) = error("Cannot setTitle")

    override fun setActions(actions: List<AppAction>) = error("Cannot setActions")

    override fun hideSystemUI() = error("Cannot hideSystemUI")

    override fun showSystemUI() = error("Cannot showSystemUI")
}

val LocalUtils = staticCompositionLocalOf { EmptyUtils }