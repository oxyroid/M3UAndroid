package com.m3u.tv.utils

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.core.util.basic.rational
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Stable
class Helper(private val activity: ComponentActivity) {

    /**
     * PiP is supported on Android TV from Android 14 (API 34) onward.
     * See https://developer.android.com/training/tv/get-started/multitasking
     */
    fun isPipSupported(): Boolean =
        activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Updated by the player screen when video size changes; used when entering PiP on Home. */
    var latestVideoSize: Rect = Rect()
        set(value) {
            field.set(value.left, value.top, value.right, value.bottom)
        }

    /** Set by the player screen; invoked when the user leaves (e.g. Home press) for SmartTube-style PiP. */
    var onUserLeaveHint: (() -> Unit)? = null

    /** Called from Activity.onUserLeaveHint(); runs the callback registered by the player. */
    fun runOnUserLeaveHint() {
        onUserLeaveHint?.invoke()
    }

    fun enterPipMode(videoSize: Rect) {
        if (!isPipSupported()) return
        val size = if (videoSize.isNotEmpty) videoSize else Rect(0, 0, 16, 9)
        val aspectRatio = if (size.isNotEmpty) size.rational else Rational(16, 9)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
        if (size.isNotEmpty) {
            builder.setSourceRectHint(size)
        }
        val params = builder.build()
        if (activity.isInPictureInPictureMode) {
            activity.setPictureInPictureParams(params)
        } else {
            activity.enterPictureInPictureMode(params)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HelperEntryPoint {
        val playerManager: PlayerManager
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication<HelperEntryPoint>(activity.applicationContext)
    }
    private val playerManager by lazy { entryPoint.playerManager }

    suspend fun play(mediaCommand: MediaCommand) {
        playerManager.play(mediaCommand)
    }

    suspend fun replay() {
        playerManager.replay()
    }
}

val LocalHelper = staticCompositionLocalOf<Helper> { error("Please provide helper.") }
