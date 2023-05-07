package com.m3u.ui.model

import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

abstract class Helper {
    abstract var title: String

    abstract var actions: List<AppAction>
    var systemUiVisibility: Boolean = true
        set(value) {
            if (field == value) return
            if (value) showSystemUI()
            else hideSystemUI()
            field = value
        }

    abstract fun enterPipMode(size: Rect)
    abstract fun hideSystemUI()
    abstract fun showSystemUI()
    abstract fun registerOnUserLeaveHintListener(callback: () -> Unit)
    abstract fun unregisterOnUserLeaveHintListener()
    abstract fun registerOnPictureInPictureModeChangedListener(
        consumer: Consumer<PictureInPictureModeChangedInfo>
    )

    abstract fun unregisterOnPictureInPictureModeChangedListener()
}

val EmptyHelper = object : Helper() {
    override var title: String
        get() = error("Cannot get title")
        set(_) {
            error("Cannot set title")
        }

    override var actions: List<AppAction>
        get() = error("Cannot get actions")
        set(_) {
            error("Cannot set actions")
        }

    override fun enterPipMode(size: Rect) = error("Cannot enterPipMode")

    override fun hideSystemUI() = error("Cannot hideSystemUI")

    override fun showSystemUI() = error("Cannot showSystemUI")

    override fun registerOnPictureInPictureModeChangedListener(
        consumer: Consumer<PictureInPictureModeChangedInfo>
    ) = error("addOnPictureInPictureModeChangedListener")


    override fun unregisterOnPictureInPictureModeChangedListener() =
        error("removeOnPictureInPictureModeChangedListener")

    override fun registerOnUserLeaveHintListener(callback: () -> Unit) =
        error("addOnUserLeaveHintListener")

    override fun unregisterOnUserLeaveHintListener() = error("unregisterOnUserLeaveHintListener")
}

val LocalHelper = staticCompositionLocalOf { EmptyHelper }