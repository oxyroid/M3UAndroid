package com.m3u.data.service

import android.graphics.Rect
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
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
    abstract val groups: StateFlow<List<Tracks.Group>>
    abstract val selected: StateFlow<Map<@C.TrackType Int, Format?>>

    abstract val url: StateFlow<String?>

    abstract fun play(url: String)
    abstract fun stop()
    abstract fun replay()
    abstract fun chooseTrack(group: TrackGroup, trackIndex: Int)
}
