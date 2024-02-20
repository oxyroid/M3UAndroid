package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Rect
import android.util.Log
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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaSession
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.annotation.ReconnectMode
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import okhttp3.OkHttpClient
import javax.inject.Inject

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val pref: Pref,
    @Dispatcher(Main) mainDispatcher: CoroutineDispatcher
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

    private val coroutineScope = CoroutineScope(mainDispatcher)

    private fun createPlayer(): ExoPlayer {
        val rf = NextRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
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

        val lc = DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()

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
                addAnalyticsListener(EventLogger())
                playWhenReady = true
                addListener(this@PlayerManagerImpl)
            }
    }

    private var listenPrefJob: Job? = null

    private var currentConnectTimeout = pref.connectTimeout
    private var currentTunneling = pref.tunneling

    private fun parseMimetype(url: String): String? {
        return when {
            url.contains(".m3u", true) ||
                    url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8

            url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
            url.contains(".ism", true) -> MimeTypes.APPLICATION_SS
            url.startsWith("rtsp://", true) ||
                    url.contains(".sdp", true) -> MimeTypes.APPLICATION_RTSP

            else -> null
        }
    }

    override fun play(url: String) {
        _url.value = url
        tryPlay(
            mimeType = parseMimetype(url)
        )

        listenPrefJob?.cancel()
        listenPrefJob = combine(
            pref.observeAsFlow { it.connectTimeout },
            pref.observeAsFlow { it.tunneling }
        )
        { timeout, tunneling ->
            if (timeout != currentConnectTimeout || tunneling != currentTunneling) {
                replay()
                currentConnectTimeout = timeout
                currentTunneling = tunneling
            }
        }
            .launchIn(coroutineScope)
    }

    private fun tryPlay(mimeType: String?) {
        val url = this.url.value ?: return
        val dataSourceFactory = buildHttpDataSourceFactory()
        val currentPlayer = _player.updateAndGet { prev ->
            prev ?: createPlayer()
        }!!

        when (mimeType) {
            MimeTypes.APPLICATION_SS -> {
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
                val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(false)
                    .createMediaSource(MediaItem.fromUri(url))
                currentPlayer.setMediaSource(hlsMediaSource)
            }

            MIMETYPE_RTMP -> {
                val factory = RtmpDataSource.Factory()
                val mediaSourceFactory = DefaultMediaSourceFactory(factory)
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .build()
                val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                currentPlayer.setMediaSource(mediaSource)
            }

            else -> {
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .apply {
                        if (mimeType != null) {
                            setMimeType(mimeType)
                        }
                    }
                    .build()
                val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                currentPlayer.setMediaSource(mediaSource)
            }
        }
        currentPlayer.prepare()
    }

    override fun release() {
        listenPrefJob?.cancel()
        listenPrefJob = null
        _player.update {
            it?.stop()
            it?.release()
            it?.removeListener(this)
            _url.value = null
            _groups.value = emptyList()
            _videoSize.value = Rect()
            _playbackError.value = null
            _playbackState.value = Player.STATE_IDLE
            mimeType = null
            null
        }
    }

    override fun replay() {
        val prev = url.value
        if (prev != null) {
            release()
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

    private var mimeType: String? = null
        set(value) {
            Log.e("PlayerManagerImpl", "tryMimeType: $value")
            field = value
        }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        super.onPlayerErrorChanged(error)
        when (error?.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                _player.value?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
            }

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                Log.e("PlayerManagerImpl", "onPlayerErrorChanged: try other mimetypes")
                when (mimeType) {
                    null -> {
                        mimeType = MimeTypes.APPLICATION_M3U8
                        tryPlay(mimeType)
                        return
                    }

                    MimeTypes.APPLICATION_M3U8 -> {
                        mimeType = MIMETYPE_RTMP
                        tryPlay(mimeType)
                        return
                    }

                    MIMETYPE_RTMP -> {
                        mimeType = MimeTypes.APPLICATION_MPD
                        tryPlay(mimeType)
                        return
                    }

                    MimeTypes.APPLICATION_MPD -> {
                        mimeType = MimeTypes.APPLICATION_SS
                        tryPlay(mimeType)
                        return
                    }

                    MimeTypes.APPLICATION_SS -> {
                        mimeType = MimeTypes.APPLICATION_RTSP
                        tryPlay(mimeType)
                        return
                    }

                    MimeTypes.APPLICATION_RTSP -> {
                        mimeType = null
                    }

                    else -> {
                        mimeType = null
                    }
                }
            }

            else -> {}
        }

        _playbackError.value = error
    }

    private val _groups = MutableStateFlow<List<Tracks.Group>>(emptyList())
    override val groups: StateFlow<List<Tracks.Group>> = _groups.asStateFlow()

    override val trackFormats: Flow<Map<@C.TrackType Int, List<Format>>> = groups
        .map { all -> all.groupBy { it.type } }
        .map { groups ->
            groups.mapValues { (_, innerGroups) ->
                innerGroups
                    .map { group -> List(group.length) { group.getTrackFormat(it) } }
                    .flatten()
            }
        }
    override val selected: Flow<Map<@C.TrackType Int, Format?>> = groups
        .map { all -> all.groupBy { it.type } }
        .map { groups ->
            groups.mapValues { (_, groups) ->
                var format: Format? = null
                outer@ for (group in groups) {
                    var selectedIndex = -1
                    inner@ for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            selectedIndex = i
                            break@inner
                        }
                    }
                    if (selectedIndex != -1) {
                        format = group.getTrackFormat(selectedIndex)
                        break@outer
                    }
                }
                format
            }
        }

    override fun chooseTrack(group: TrackGroup, trackIndex: Int) {
        val currentPlayer = _player.value ?: return
        val type = group.type
        val override = TrackSelectionOverride(group, trackIndex)
        currentPlayer.trackSelectionParameters = currentPlayer
            .trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .setTrackTypeDisabled(type, false)
            .build()
    }

    override fun clearTrack(type: @C.TrackType Int) {
        val currentPlayer = _player.value ?: return
        currentPlayer.trackSelectionParameters = currentPlayer
            .trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(type, true)
            .build()
    }

    private fun buildHttpDataSourceFactory(): DataSource.Factory {
        //  Credentials.basic()
        return OkHttpDataSource.Factory(okHttpClient)
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        _groups.value = tracks.groups
    }
}

private fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

private const val MIMETYPE_RTMP = "rtmp"
