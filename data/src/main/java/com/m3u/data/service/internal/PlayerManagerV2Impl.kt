package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Rect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaSession
import com.m3u.codec.Codecs
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.annotation.ReconnectMode
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.BuildConfig
import com.m3u.data.SSLs
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.PlayerManagerV2
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject

class PlayerManagerV2Impl @Inject constructor(
    @Dispatcher(Main) mainDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val pref: Pref,
    private val playlistRepository: PlaylistRepository,
    private val streamRepository: StreamRepository,
    before: Logger
) : PlayerManagerV2, Player.Listener, MediaSession.Callback {
    private val mainCoroutineScope = CoroutineScope(mainDispatcher)
    private val ioCoroutineScope = CoroutineScope(ioDispatcher)

    private val logger = before.prefix("player-manager")
    private val json = Json {
        prettyPrint = true
    }

    private fun Logger.debug(block: () -> String) {
        if (BuildConfig.DEBUG) {
            log(block())
        }
    }

    override val player = MutableStateFlow<ExoPlayer?>(null)
    override val size = MutableStateFlow(Rect())

    private val streamId = MutableStateFlow<Int?>(null)

    override val stream = streamId
        .onEach { logger.debug { "streamId: $it" } }
        .flatMapLatest { streamId ->
            streamId?.let { streamRepository.observe(it) } ?: flowOf(null)
        }
        .distinctUntilChanged { old, new ->
            when {
                old == null && new == null -> true
                old == null -> false
                new == null -> false
                else -> old like new
            }
        }
        .onEach { logger.debug { "stream: ${json.encodeToString(it)}" } }
        .flowOn(ioDispatcher)
        .onEach { stream ->
            val streamUrl = stream?.url ?: return@onEach
            val wrapper = MimetypeWrapper.Unspecified(streamUrl).next()
            logger.debug { "init wrapper: $wrapper" }
            this.wrapper = wrapper
            when (wrapper) {
                is MimetypeWrapper.Maybe -> play(mimeType = wrapper.mimeType, url = streamUrl)
                is MimetypeWrapper.Trying -> play(mimeType = wrapper.mimeType, url = streamUrl)
                else -> return@onEach
            }

            observePreferencesChangingJob?.cancel()
            observePreferencesChangingJob = mainCoroutineScope.launch {
                observePreferencesChanging { timeout, tunneling ->
                    if (timeout != currentConnectTimeout || tunneling != currentTunneling) {
                        logger.debug { "preferences changed, replaying..." }
                        replay()
                        currentConnectTimeout = timeout
                        currentTunneling = tunneling
                    }
                }
            }
        }
        .flowOn(mainDispatcher)
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.Lazily
        )

    override val playlist = stream.flatMapLatest { stream ->
        stream?.let { playlistRepository.observe(it.playlistUrl) } ?: flowOf(null)
    }
        .distinctUntilChanged { old, new ->
            when {
                old == null && new == null -> true
                old == null -> false
                new == null -> false
                else -> old like new
            }
        }
        .onEach {
            logger.debug { "playlist: ${json.encodeToString(it)}" }
        }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.Lazily
        )

    override val playbackState = MutableStateFlow<@Player.State Int>(Player.STATE_IDLE)
    override val playbackException = MutableStateFlow<PlaybackException?>(null)
    override val tracksGroups = MutableStateFlow<List<Tracks.Group>>(emptyList())

    private var currentConnectTimeout = pref.connectTimeout
    private var currentTunneling = pref.tunneling
    private var observePreferencesChangingJob: Job? = null

    override suspend fun play(streamId: Int) {
        this.streamId.value = streamId
    }

    private fun play(
        mimeType: String?,
        url: String = stream.value?.url.orEmpty(),
        userAgent: String? = playlist.value?.userAgent,
        rtmp: Boolean = Url(url).protocol.name == "rtmp",
        tunneling: Boolean = pref.tunneling
    ) {
        logger.debug { "play, mimetype: $mimeType, url: $url, user-agent: $userAgent, rtmp: $rtmp, tunneling: $tunneling" }
        val dataSourceFactory = if (rtmp) {
            RtmpDataSource.Factory()
        } else {
            createHttpDataSourceFactory(userAgent)
        }
        val mediaSourceFactory = when (mimeType) {
            MimeTypes.APPLICATION_M3U8 -> HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(false)

            MimeTypes.APPLICATION_SS -> ProgressiveMediaSource.Factory(dataSourceFactory)
            MimeTypes.APPLICATION_RTSP -> RtspMediaSource.Factory()
                .setDebugLoggingEnabled(true)
                .setForceUseRtpTcp(true)
                .setSocketFactory(SSLs.TLSTrustAll.socketFactory)

            else -> DefaultMediaSourceFactory(dataSourceFactory)
        }
        val player = player.updateAndGet { prev ->
            prev ?: createPlayer(mediaSourceFactory, tunneling)
        }!!
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
    }

    override suspend fun replay() {
        val streamId = stream.value?.id
        release()
        streamId?.let { play(it) }
    }

    override fun release() {
        observePreferencesChangingJob?.cancel()
        observePreferencesChangingJob = null
        player.update {
            it ?: return
            it.stop()
            it.release()
            it.removeListener(this)
            streamId.value = null
            size.value = Rect()
            playbackState.value = Player.STATE_IDLE
            playbackException.value = null
            tracksGroups.value = emptyList()
            wrapper = MimetypeWrapper.Unsupported
            null
        }
    }

    override fun chooseTrack(group: TrackGroup, index: Int) {
        val currentPlayer = player.value ?: return
        val type = group.type
        val override = TrackSelectionOverride(group, index)
        currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .setTrackTypeDisabled(type, false)
            .build()
    }

    override fun clearTrack(type: @C.TrackType Int) {
        val currentPlayer = player.value ?: return
        currentPlayer.trackSelectionParameters = currentPlayer
            .trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(type, true)
            .build()
    }

    private fun createPlayer(
        mediaSourceFactory: MediaSource.Factory,
        tunneling: Boolean
    ): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setRenderersFactory(renderersFactory)
        .setTrackSelector(createTrackSelector(tunneling))
        .setLoadControl(loadControl)
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
            addListener(this@PlayerManagerV2Impl)
        }

    private val renderersFactory: RenderersFactory by lazy {
        Codecs.createRenderersFactory(context)
    }

    private val loadControl: LoadControl by lazy {
        DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .build()
    }

    private fun createTrackSelector(tunneling: Boolean): TrackSelector {
        return DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    .setTunnelingEnabled(tunneling)
            )
        }
    }

    private fun createHttpDataSourceFactory(userAgent: String?): DataSource.Factory {
        return OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
    }

    private suspend fun observePreferencesChanging(
        onChanged: suspend (timeout: Long, tunneling: Boolean) -> Unit
    ): Unit = coroutineScope {
        combine(
            pref.observeAsFlow { it.connectTimeout },
            pref.observeAsFlow { it.tunneling }
        ) { timeout, tunneling ->
            onChanged(timeout, tunneling)
        }
            .collect()
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        size.value = videoSize.toRect()
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        playbackState.value = state
        if (state == Player.STATE_ENDED && pref.reconnectMode == ReconnectMode.RECONNECT) {
            mainCoroutineScope.launch { replay() }
        }
    }

    private var wrapper: MimetypeWrapper = MimetypeWrapper.Unsupported

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        super.onPlayerErrorChanged(error)
        when (error?.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                logger.debug { "error! behind live window" }
                player.value?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
            }

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                if (wrapper.hasNext()) {
                    val next = wrapper.next()
                    logger.debug { "[${PlaybackException.getErrorCodeName(error.errorCode)}] Try another mimetype, from $wrapper to $next" }
                    wrapper = next
                    when (next) {
                        is MimetypeWrapper.Maybe -> play(next.mimeType)
                        is MimetypeWrapper.Trying -> play(next.mimeType)
                        else -> {
                            playbackException.value = error
                        }
                    }
                }
            }

            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {}

            else -> {
                if (error != null) {
                    logger.debug {"[${PlaybackException.getErrorCodeName(error.errorCode)}] See player for detail" }
                }
                playbackException.value = error
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        tracksGroups.value = tracks.groups
    }
}

private fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

private sealed class MimetypeWrapper : Iterator<MimetypeWrapper> {
    class Unspecified(val url: String) : MimetypeWrapper()
    class Maybe(val mimeType: String) : MimetypeWrapper()
    class Trying(val mimeType: String) : MimetypeWrapper()
    object Unsupported : MimetypeWrapper()

    override fun toString(): String = when (this) {
        is Maybe -> "Maybe[$mimeType]"
        is Trying -> "Trying[$mimeType]"
        is Unspecified -> "Unspecified[$url]"
        Unsupported -> "Unsupported"
    }

    override fun hasNext(): Boolean = this != Unsupported

    override fun next(): MimetypeWrapper = when (this) {
        is Unspecified -> {
            when {
                url.contains(".m3u", true) ||
                        url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8

                url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
                url.contains(".ism", true) -> MimeTypes.APPLICATION_SS
                url.startsWith("rtsp://", true) ||
                        url.contains(".sdp", true) -> MimeTypes.APPLICATION_RTSP
                // cannot determine mine-type
                else -> null
            }
                .let { maybeMimetype ->
                    maybeMimetype?.let { Maybe(it) } ?: Trying(MimeTypes.APPLICATION_M3U8)
                }
        }

        is Maybe -> Trying(MimeTypes.APPLICATION_M3U8)
        is Trying -> when (mimeType) {
            MimeTypes.APPLICATION_M3U8 -> Trying(MimeTypes.APPLICATION_MPD)
            MimeTypes.APPLICATION_MPD -> Trying(MimeTypes.APPLICATION_SS)
            MimeTypes.APPLICATION_SS -> Trying(MimeTypes.APPLICATION_RTSP)
            else -> Unsupported
        }

        else -> throw IllegalArgumentException()
    }
}
