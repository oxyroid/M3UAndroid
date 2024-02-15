package com.m3u.ui.helper

import android.content.Context
import android.graphics.Rect
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.wrapper.Message
import com.m3u.data.television.model.RemoteDirection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

typealias OnUserLeaveHint = () -> Unit
typealias OnPipModeChanged = Consumer<PictureInPictureModeChangedInfo>

@Stable
interface Helper {
    var title: String
    var actions: ImmutableList<Action>
    var fob: Fob?
    var statusBarVisibility: UBoolean
    var navigationBarVisibility: UBoolean
    var onUserLeaveHint: OnUserLeaveHint?
    var onPipModeChanged: OnPipModeChanged?
    var darkMode: UBoolean
    var brightness: Float
    val isInPipMode: Boolean
    var screenOrientation: Int
    val message: StateFlow<Message>
    var deep: Int
    val activityContext: Context
    val remoteDirection: SharedFlow<RemoteDirection>

    @get:Composable
    val windowSizeClass: WindowSizeClass

    fun enterPipMode(size: Rect)
    fun toast(message: String)
    fun play(url: String)
    fun replay()
}

val Helper.useRailNav: Boolean
    @Composable get() = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact

val LocalHelper = staticCompositionLocalOf { EmptyHelper }
