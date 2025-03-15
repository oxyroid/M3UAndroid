package com.m3u.tv.utils

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Stable
class Helper(private val activity: ComponentActivity) {
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