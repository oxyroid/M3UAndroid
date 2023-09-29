package com.m3u.core.architecture.service

import android.graphics.Rect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

abstract class PlayerManager {
    abstract fun observe(): Flow<Player?>
    val videoSize: MutableStateFlow<Rect> = MutableStateFlow(Rect())
    val playbackState: MutableStateFlow<@Player.State Int> = MutableStateFlow(Player.STATE_IDLE)
    val playerError: MutableStateFlow<PlaybackException?> = MutableStateFlow(null)

    abstract fun installMedia(url: String)
    abstract fun uninstallMedia()
    abstract fun initialize()
}