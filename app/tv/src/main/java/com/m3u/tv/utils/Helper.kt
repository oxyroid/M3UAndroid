package com.m3u.tv.utils

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.m3u.data.repository.playlist.PlaylistRepository
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
        val playlistRepository: PlaylistRepository
        val playerManager: PlayerManager
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication<HelperEntryPoint>(activity.applicationContext)
    }

    suspend fun play(mediaCommand: MediaCommand) {
        entryPoint.playerManager.play(mediaCommand)
    }

    suspend fun replay() {
        entryPoint.playerManager.replay()
    }
}

val LocalHelper = staticCompositionLocalOf<Helper> { error("Please provide helper.") }