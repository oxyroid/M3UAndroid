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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.session.MediaSession
import com.m3u.codec.Codecs
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.post
import com.m3u.core.architecture.logger.prefix
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.annotation.ReconnectMode
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.SSLs
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.data.database.model.copyXtreamEpisode
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManagerV2
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
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

    override val player = MutableStateFlow<ExoPlayer?>(null)
    override val size = MutableStateFlow(Rect())

    private val mediaCommand = MutableStateFlow<MediaCommand?>(null)

    override val stream: StateFlow<Stream?> = mediaCommand
        .onEach { logger.post { "receive media command: $it" } }
        .flatMapLatest { input ->
            when (input) {
                is MediaCommand.Live -> streamRepository.observe(input.streamId)
                is MediaCommand.XtreamEpisode -> streamRepository
                    .observe(input.streamId)
                    .map { it?.copyXtreamEpisode(input.episode) }

                else -> flowOf(null)
            }
        }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    override val playlist: StateFlow<Playlist?> = mediaCommand.flatMapLatest { command ->
        val stream = command?.let { streamRepository.get(it.streamId) }
        stream?.let { playlistRepository.observe(it.playlistUrl) } ?: flowOf(null)
    }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    override val playbackState = MutableStateFlow<@Player.State Int>(Player.STATE_IDLE)
    override val playbackException = MutableStateFlow<PlaybackException?>(null)
    override val tracksGroups = MutableStateFlow<List<Tracks.Group>>(emptyList())

    private var currentConnectTimeout = pref.connectTimeout
    private var currentTunneling = pref.tunneling
    private var observePreferencesChangingJob: Job? = null

    override suspend fun play(command: MediaCommand) {
        release()
        mediaCommand.value = command
        val stream = when (command) {
            is MediaCommand.Live -> streamRepository.get(command.streamId)
            is MediaCommand.XtreamEpisode -> streamRepository
                .get(command.streamId)
                ?.copyXtreamEpisode(command.episode)
        }
        if (stream != null) {
            val streamUrl = stream.url
            streamRepository.reportPlayed(stream.id)

            val wrapper = MimetypeWrapper.Unspecified(streamUrl).next()
            mimetypeWrapper = wrapper
            logger.post { "init wrapper: $wrapper" }

            when (wrapper) {
                is MimetypeWrapper.Maybe -> tryPlay(
                    mimeType = wrapper.mimeType,
                    url = streamUrl
                )

                is MimetypeWrapper.Trying -> tryPlay(
                    mimeType = wrapper.mimeType,
                    url = streamUrl
                )

                else -> return
            }

            observePreferencesChangingJob?.cancel()
            observePreferencesChangingJob = mainCoroutineScope.launch {
                observePreferencesChanging { timeout, tunneling ->
                    if (timeout != currentConnectTimeout || tunneling != currentTunneling) {
                        logger.post { "preferences changed, replaying..." }
                        replay()
                        currentConnectTimeout = timeout
                        currentTunneling = tunneling
                    }
                }
            }
        }
    }

    private fun tryPlay(
        mimeType: String?,
        url: String = stream.value?.url.orEmpty(),
        userAgent: String? = playlist.value?.userAgent,
        rtmp: Boolean = Url(url).protocol.name == "rtmp",
        tunneling: Boolean = pref.tunneling
    ) {
        logger.post { "play, mimetype: $mimeType, url: $url, user-agent: $userAgent, rtmp: $rtmp, tunneling: $tunneling" }
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
        logger.post { "media-source-factory: ${mediaSourceFactory::class.qualifiedName}" }
        val player = player.updateAndGet { prev ->
            prev ?: createPlayer(mediaSourceFactory, tunneling)
        }!!
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
    }

    override suspend fun replay() {
        val prev = mediaCommand.value
        release()
        prev?.let { play(it) }
    }

    override fun release() {
        observePreferencesChangingJob?.cancel()
        observePreferencesChangingJob = null
        player.update {
            it ?: return
            it.stop()
            it.release()
            it.removeListener(this)
            mediaCommand.value = null
            size.value = Rect()
            playbackState.value = Player.STATE_IDLE
            playbackException.value = null
            tracksGroups.value = emptyList()
            mimetypeWrapper = MimetypeWrapper.Unsupported
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
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            val attributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(attributes, true)
            playWhenReady = true
            addListener(this@PlayerManagerV2Impl)
        }

    private val renderersFactory: RenderersFactory by lazy {
        Codecs.createRenderersFactory(context)
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
        logger.post { "playback-state: $state" }
        when (state) {
            Player.STATE_READY -> {
                logger.post {
                    val currentPlayer = player.value ?: return@post ""
                    val getCurrentMediaItem =
                        currentPlayer.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                    if (!getCurrentMediaItem) {
                        return@post "get-current-media-item: false"
                    }
                    val seekable = currentPlayer.isCurrentMediaItemSeekable
                    val dynamic = currentPlayer.isCurrentMediaItemDynamic
                    "get-current-media-item: $getCurrentMediaItem, dynamic: $dynamic, seekable: $seekable"
                }
            }

            else -> {}
        }
    }

    private var mimetypeWrapper: MimetypeWrapper = MimetypeWrapper.Unsupported

    override fun onPlayerErrorChanged(exception: PlaybackException?) {
        super.onPlayerErrorChanged(exception)
        when (exception?.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                logger.post { "error! behind live window" }
                player.value?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
            }

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                if (mimetypeWrapper.hasNext()) {
                    val next = mimetypeWrapper.next()
                    logger.post {
                        "[${PlaybackException.getErrorCodeName(exception.errorCode)}] " +
                                "Try another mimetype, from $mimetypeWrapper to $next"
                    }
                    mimetypeWrapper = next
                    when (next) {
                        is MimetypeWrapper.Maybe -> tryPlay(next.mimeType)
                        is MimetypeWrapper.Trying -> tryPlay(next.mimeType)
                        else -> {
                            playbackException.value = exception
                        }
                    }
                }
            }

            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {}

            else -> {
                if (exception != null) {
                    logger.post {
                        "[${PlaybackException.getErrorCodeName(exception.errorCode)}] See player for detail"
                    }
                }
                playbackException.value = exception
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        tracksGroups.value = tracks.groups
    }

    private val logger = before.prefix("player-manager")
}

private fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

private sealed class MimetypeWrapper : Iterator<MimetypeWrapper> {
    class Unspecified(val url: String) : MimetypeWrapper()
    class Maybe(val mimeType: String) : MimetypeWrapper()
    class Trying(val mimeType: String) : MimetypeWrapper()
    object Unsupported : MimetypeWrapper()

    companion object {
        val ORDER_DEFAULT = arrayOf(
            MimeTypes.APPLICATION_SS,
            MimeTypes.APPLICATION_M3U8,
            MimeTypes.APPLICATION_MPD,
            MimeTypes.APPLICATION_RTSP
        )
    }

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
                    maybeMimetype?.let { Maybe(it) } ?: Trying(ORDER_DEFAULT.first())
                }
        }

        is Maybe -> Trying(ORDER_DEFAULT.first())
        is Trying -> {
            ORDER_DEFAULT
                .getOrNull(ORDER_DEFAULT.indexOf(mimeType) + 1)
                ?.let { Trying(it) }
                ?: Unsupported
        }

        else -> throw IllegalArgumentException()
    }
}
