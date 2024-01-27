@file:OptIn(UnstableApi::class)

package com.m3u.data.manager.impl

import android.content.Context
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.annotation.ReconnectMode
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.manager.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import javax.inject.Inject

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pref: Pref
) : PlayerManager, Player.Listener, MediaSession.Callback {
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    override val player: Flow<Player?> = _player.asStateFlow()

    private val _url = MutableStateFlow<String?>(null)
    override val url: StateFlow<String?> = _url.asStateFlow()

    private val _videoSize = MutableStateFlow(Rect())
    override val videoSize: StateFlow<Rect> = _videoSize.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    override val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _playbackError = MutableStateFlow<PlaybackException?>(null)
    override val playerError: StateFlow<PlaybackException?> = _playbackError.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        combine(
            pref.observeAsFlow { it.connectTimeout },
            pref.observeAsFlow { it.tunneling }
        ) { _, _ -> replay() }
            .launchIn(coroutineScope)
    }

    private fun createPlayer(): ExoPlayer {
        val rf = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }
        val dsf = DefaultDataSource.Factory(
            context,
            buildHttpDataSourceFactory()
        )
        val msf = DefaultMediaSourceFactory(dsf)

        val ts = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    .setTunnelingEnabled(pref.tunneling)
            )
        }

        val lc = DefaultLoadControl()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(msf)
            .setRenderersFactory(rf)
            .setTrackSelector(ts)
            .setLoadControl(lc)
            .setAnalyticsCollector(DefaultAnalyticsCollector(SystemClock.DEFAULT))
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                val attributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(attributes, true)
                playWhenReady = true
                addListener(this@PlayerManagerImpl)
            }
    }

    override fun play(url: String) {
        _url.value = url
        tryPlay(
            mimeType = null
        )
    }

    private fun tryPlay(mimeType: String?) {
        val url = this.url.value ?: return
        val currentPlayer = _player.value ?: createPlayer().also {
            _player.value.also { prev ->
                prev?.stop()
                prev?.release()
                prev?.removeListener(this)
            }
            _player.value = it
        }

        when (mimeType) {
            MimeTypes.APPLICATION_SS -> {
                val dataSourceFactory = buildHttpDataSourceFactory()
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))

                currentPlayer.setMediaSource(mediaSource)
            }

            MimeTypes.APPLICATION_RTSP -> {
                val mediaSource = RtspMediaSource.Factory()
                    .createMediaSource(MediaItem.fromUri(url))
                currentPlayer.setMediaSource(mediaSource)
            }

            MimeTypes.APPLICATION_M3U8 -> {
                val dataSourceFactory = buildHttpDataSourceFactory()
                val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(false)
                    .createMediaSource(MediaItem.fromUri(url))
                val player = ExoPlayer.Builder(context).build()
                player.setMediaSource(hlsMediaSource)
            }

            else -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .apply {
                        if (mimeType != null) {
                            setMimeType(mimeType)
                        }
                    }
                    .build()

                currentPlayer.setMediaItem(mediaItem)
            }
        }
        currentPlayer.prepare()
    }

    override fun stop() {
        _player.update {
            it?.stop()
            it?.release()
            it?.removeListener(this)
            null
        }
        _url.value = null
        _groups.value = emptyList()
        _videoSize.value = Rect()
        _playbackError.value = null
        _playbackState.value = Player.STATE_IDLE
    }

    override fun replay() {
        val prev = url.value
        if (prev != null) {
            stop()
            play(prev)
        }
    }

    override fun onVideoSizeChanged(size: VideoSize) {
        super.onVideoSizeChanged(size)
        _videoSize.value = size.toRect()
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        _playbackState.value = state
        if (state == Player.STATE_ENDED && pref.reconnectMode == ReconnectMode.RECONNECT) {
            _player.value?.let {
                it.seekToDefaultPosition()
                it.prepare()
            }
        }
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        super.onPlayerErrorChanged(error)
        when (error?.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                // always retry
                // if (pref.reconnectMode != ReconnectMode.NO) {
                    _player.value?.let {
                        it.seekToDefaultPosition()
                        it.prepare()
                    }
                // }
            }

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                tryPlay(
                    mimeType = MimeTypes.APPLICATION_M3U8
                )
            }

            else -> {}
        }

        _playbackError.value = error
    }

    private val _groups = MutableStateFlow<List<Tracks.Group>>(emptyList())
    override val groups: StateFlow<List<Tracks.Group>> = _groups.asStateFlow()

    override val selected: Flow<Map<@C.TrackType Int, Format?>> = groups.map { all ->
        all
            .filter { it.isSelected }
            .groupBy { it.type }
            .mapValues { (_, groups) ->
                val group = groups.first()
                var selectedIndex = 0
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        selectedIndex = i
                        break
                    }
                }
                group.getTrackFormat(selectedIndex)
            }
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        _groups.value = tracks.groups
    }

    override fun chooseTrack(group: TrackGroup, trackIndex: Int) {
        val currentPlayer = _player.value ?: return
        val override = TrackSelectionOverride(group, trackIndex)
        currentPlayer.trackSelectionParameters = currentPlayer
            .trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }

    private fun buildHttpDataSourceFactory(): DataSource.Factory {
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        CookieHandler.setDefault(cookieManager)
        return DefaultHttpDataSource.Factory().apply {
            setAllowCrossProtocolRedirects(true)
        }
    }
}

private fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}
