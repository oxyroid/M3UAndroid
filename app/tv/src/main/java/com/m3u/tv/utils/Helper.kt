package com.m3u.tv.utils

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

    fun enterPipMode(videoSize: Rect) {
        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(
                if (videoSize.isNotEmpty) videoSize.rational
                else Rational(16, 9)
            )
            .build()
        activity.enterPictureInPictureMode(params)
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
