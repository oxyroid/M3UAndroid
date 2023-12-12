package com.m3u.data.service

import android.graphics.Rect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class PlayerManager {
    abstract fun observe(): Flow<Player?>
    protected val mutableVideoSize = MutableStateFlow(Rect())
    val videoSize: StateFlow<Rect> = mutableVideoSize.asStateFlow()
    protected val mutablePlaybackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<@Player.State Int> = mutablePlaybackState.asStateFlow()
    protected val mutablePlaybackError = MutableStateFlow<PlaybackException?>(null)
    val playerError: StateFlow<PlaybackException?> = mutablePlaybackError.asStateFlow()

    abstract val url: StateFlow<String?>

    abstract fun play(url: String)
    abstract fun stop()
    abstract fun replay()
}
