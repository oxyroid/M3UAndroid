package com.m3u.ui.helper

import android.content.Context
import android.graphics.Rect
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.wrapper.Message
import com.m3u.data.television.model.RemoteDirection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

val EmptyHelper = object : Helper {
    override var title: String
        get() = error("Cannot get title")
        set(_) {
            error("Cannot set title")
        }

    override var actions: ImmutableList<Action>
        get() = error("Cannot get actions")
        set(_) {
            error("Cannot set actions")
        }
    override var fob: Fob?
        get() = error("Cannot get fob")
        set(_) {
            error("Cannot set fob")
        }
    override val message: StateFlow<Message>
        get() = MutableStateFlow(Message.Dynamic.EMPTY)

    override var statusBarVisibility: UBoolean
        get() = error("Cannot get systemUiVisibility")
        set(_) {
            error("Cannot set systemUiVisibility")
        }
    override var navigationBarVisibility: UBoolean
        get() = error("Cannot get navigationBarsVisibility")
        set(_) {
            error("Cannot set navigationBarsVisibility")
        }

    override var deep: Int
        get() = error("Cannot get deep")
        set(_) {
            error("Cannot set deep")
        }

    override val activityContext: Context
        get() = error("Cannot get activityContext")

    override var darkMode: UBoolean
        get() = error("Cannot get darkMode")
        set(_) {
            error("Cannot set darkMode")
        }

    override var onUserLeaveHint: OnUserLeaveHint?
        get() = error("Cannot get onUserLeaveHint")
        set(_) {
            error("Cannot set onUserLeaveHint")
        }
    override var onPipModeChanged: OnPipModeChanged?
        get() = error("Cannot get onPipModeChanged")
        set(_) {
            error("Cannot set onPipModeChanged")
        }

    override var brightness: Float
        get() = error("Cannot get brightness")
        set(_) {
            error("Cannot set brightness")
        }

    override val isInPipMode: Boolean
        get() = error("Cannot get isInPipMode")

    override var screenOrientation: Int
        get() = error("Cannot get screenOrientation")
        set(_) {
            error("Cannot set screenOrientation")
        }

    override val windowSizeClass: WindowSizeClass
        @Composable get() = error("Cannot get windowSizeClass")

    override val remoteDirection: SharedFlow<RemoteDirection>
        get() = error("Cannot get remote direction")

    override fun enterPipMode(size: Rect) = error("Cannot enterPipMode")
    override fun toast(message: String) {
        error("Cannot toast: $message")
    }

    override fun play(url: String) {
        error("Cannot play stream url: $url")
    }

    override fun replay() {
        error("Cannot replay")
    }
}
