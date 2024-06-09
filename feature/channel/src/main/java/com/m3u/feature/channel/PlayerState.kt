package com.m3u.feature.channel

import android.graphics.Rect
import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

@Immutable
internal data class PlayerState(
    val playState: @Player.State Int = Player.STATE_IDLE,
    val videoSize: Rect = Rect(),
    val playerError: PlaybackException? = null,
    val player: Player? = null,
    val isPlaying: Boolean = false
)
