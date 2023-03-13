package com.m3u.ui.model

import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf

abstract class Helper {
    abstract var title: String
    var systemUiVisibility: Boolean = true
        set(value) {
            if (field == value) return
            if (value) showSystemUI()
            else hideSystemUI()
            field = value
        }

    abstract fun enterPipMode(size: Rect)
    abstract fun actions(actions: List<AppAction> = emptyList())
    abstract fun hideSystemUI()
    abstract fun showSystemUI()
}

fun Helper.actions(vararg actions: AppAction) = actions(actions.toList())

val EmptyHelper = object : Helper() {
    override var title: String
        get() = error("Cannot get title")
        set(_) {
            error("Cannot set title")
        }

    override fun enterPipMode(size: Rect) = error("Cannot enterPipMode")

    override fun actions(actions: List<AppAction>) = error("Cannot setActions")

    override fun hideSystemUI() = error("Cannot hideSystemUI")

    override fun showSystemUI() = error("Cannot showSystemUI")
}

val LocalHelper = staticCompositionLocalOf { EmptyHelper }