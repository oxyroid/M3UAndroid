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
import com.m3u.core.wrapper.Message
import com.m3u.data.service.MediaCommand
import kotlinx.coroutines.flow.StateFlow

typealias OnUserLeaveHint = () -> Unit
typealias OnPipModeChanged = Consumer<PictureInPictureModeChangedInfo>

@Stable
interface Helper {
    var title: String
    var actions: List<Action>
    var fob: Fob?
    var statusBarVisibility: Boolean?
    var navigationBarVisibility: Boolean?
    var onUserLeaveHint: OnUserLeaveHint?
    var onPipModeChanged: OnPipModeChanged?
    var isSystemBarUseDarkMode: Boolean?
    var brightness: Float
    val isInPipMode: Boolean
    var screenOrientation: Int
    val message: StateFlow<Message>
    val activityContext: Context

    @get:Composable
    val windowSizeClass: WindowSizeClass

    fun enterPipMode(size: Rect)
    fun toast(message: String)
    fun snack(message: String)
    suspend fun play(mediaCommand: MediaCommand)
    suspend fun replay()
}

val Helper.useRailNav: Boolean
    @Composable get() = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact

val LocalHelper = staticCompositionLocalOf<Helper> { error("Please provide helper.") }
