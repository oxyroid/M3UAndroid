package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Rect
import androidx.compose.runtime.snapshotFlow
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
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
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
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.post
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.core.architecture.preferences.ReconnectMode
import com.m3u.data.SSLs
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.copyXtreamEpisode
import com.m3u.data.database.model.copyXtreamSeries
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class PlayerManagerImpl @Inject constructor(
    @Dispatcher(Main) mainDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    @OkhttpClient(false) private val okHttpClient: OkHttpClient,
    private val preferences: Preferences,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val cache: Cache,
    downloadManager: DownloadManager,
    delegate: Logger
) : PlayerManager, Player.Listener, MediaSession.Callback {
    private val mainCoroutineScope = CoroutineScope(mainDispatcher)
    private val ioCoroutineScope = CoroutineScope(ioDispatcher)

    override val player = MutableStateFlow<ExoPlayer?>(null)
    override val size = MutableStateFlow(Rect())

    private val mediaCommand = MutableStateFlow<MediaCommand?>(null)

    override val channel: StateFlow<Channel?> = mediaCommand
        .onEach { logger.post { "receive media command: $it" } }
        .flatMapLatest { command ->
            when (command) {
                is MediaCommand.Common -> channelRepository.observe(command.channelId)
                is MediaCommand.XtreamEpisode -> channelRepository
                    .observe(command.channelId)
                    .map { it?.copyXtreamEpisode(command.episode) }

                else -> flowOf(null)
            }
        }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    override val playlist: StateFlow<Playlist?> = mediaCommand.flatMapLatest { command ->
        when (command) {
            is MediaCommand.Common -> {
                val channel = channelRepository.get(command.channelId)
                channel?.let { playlistRepository.observe(it.playlistUrl) } ?: flow { }
            }

            is MediaCommand.XtreamEpisode -> {
                val channel = channelRepository.get(command.channelId)
                channel?.let {
                    playlistRepository
                        .observe(it.playlistUrl)
                        .map { prev -> prev?.copyXtreamSeries(channel) }
                } ?: flowOf(null)
            }

            null -> flowOf(null)
        }
    }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    override val playbackState = MutableStateFlow<@Player.State Int>(Player.STATE_IDLE)
    override val playbackException = MutableStateFlow<PlaybackException?>(null)
    override val isPlaying = MutableStateFlow(false)
    override val tracksGroups = MutableStateFlow<List<Tracks.Group>>(emptyList())

    private var currentConnectTimeout = preferences.connectTimeout
    private var currentTunneling = preferences.tunneling
    private var currentCache = preferences.cache
    private var observePreferencesChangingJob: Job? = null

    override suspend fun play(command: MediaCommand) {
        release()
        mediaCommand.value = command
        val channel = when (command) {
            is MediaCommand.Common -> channelRepository.get(command.channelId)
            is MediaCommand.XtreamEpisode -> channelRepository
                .get(command.channelId)
                ?.copyXtreamEpisode(command.episode)
        }
        if (channel != null) {
            val channelUrl = channel.url
            val licenseType = channel.licenseType.orEmpty()
            val licenseKey = channel.licenseKey.orEmpty()
            channelRepository.reportPlayed(channel.id)
            val playlist = playlistRepository.get(channel.playlistUrl)

            val iterator = MimetypeIterator.Unspecified(channelUrl)
            this.iterator = iterator
            logger.post { "init mimetype: $iterator" }
            tryPlay(
                mimeType = null,
                url = channelUrl,
                userAgent = playlist?.userAgent,
                licenseType = licenseType,
                licenseKey = licenseKey
            )

            observePreferencesChangingJob?.cancel()
            observePreferencesChangingJob = mainCoroutineScope.launch {
                observePreferencesChanging { timeout, tunneling, cache ->
                    if (timeout != currentConnectTimeout || tunneling != currentTunneling || cache != currentCache) {
                        logger.post { "preferences changed, replaying..." }
                        replay()
                        currentConnectTimeout = timeout
                        currentTunneling = tunneling
                        currentCache = cache
                    }
                }
            }
        }
    }

    private val downloads: StateFlow<List<Download>> = downloadManager
        .observeDownloads()
        .flowOn(ioDispatcher)
        .stateIn(
            scope = ioCoroutineScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private fun tryPlay(
        mimeType: String?,
        url: String = channel.value?.url.orEmpty(),
        userAgent: String? = playlist.value?.userAgent,
        licenseType: String = channel.value?.licenseType.orEmpty(),
        licenseKey: String = channel.value?.licenseKey.orEmpty(),
    ) {
        val rtmp: Boolean = Url(url).protocol.name == "rtmp"
        val tunneling: Boolean = preferences.tunneling
        logger.post {
            "play, mimetype: $mimeType," +
                    " url: $url," +
                    " user-agent: $userAgent," +
                    " rtmp: $rtmp, " +
                    "tunneling: $tunneling"
        }
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
        if (licenseType.isNotEmpty()) {
            val drmCallback = when {
                (licenseType in arrayOf(Channel.LICENSE_TYPE_CLEAR_KEY, Channel.LICENSE_TYPE_CLEAR_KEY_2)) &&
                        !licenseKey.startsWith("http") -> LocalMediaDrmCallback(licenseKey.toByteArray())

                else -> HttpMediaDrmCallback(
                    licenseKey,
                    dataSourceFactory
                )
            }
            val uuid = when (licenseType) {
                Channel.LICENSE_TYPE_CLEAR_KEY, Channel.LICENSE_TYPE_CLEAR_KEY_2 -> C.CLEARKEY_UUID
                Channel.LICENSE_TYPE_WIDEVINE -> C.WIDEVINE_UUID
                Channel.LICENSE_TYPE_PLAY_READY -> C.PLAYREADY_UUID
                else -> C.UUID_NIL
            }
            if (uuid != C.UUID_NIL && FrameworkMediaDrm.isCryptoSchemeSupported(uuid)) {
                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(licenseType !in arrayOf(Channel.LICENSE_TYPE_CLEAR_KEY, Channel.LICENSE_TYPE_CLEAR_KEY_2))
                    .build(drmCallback)
                mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
            }
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
            iterator = MimetypeIterator.Unsupported
            null
        }
    }

    override fun clearCache() {
        cache.keys.forEach {
            cache.getCachedSpans(it)
                .forEach { span ->
                    cache.removeSpan(span)
                }
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

    override val cacheSpace: Flow<Long> = flow {
        while (true) {
            emit(cache.cacheSpace)
            delay(1.seconds)
        }
    }
        .flowOn(ioDispatcher)

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
            addListener(this@PlayerManagerImpl)
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
        val upstream = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
        return if (preferences.cache) {
            CacheDataSource.Factory()
                .setUpstreamDataSourceFactory(upstream)
                .setCache(cache)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else upstream
    }

    private suspend fun observePreferencesChanging(
        onChanged: suspend (timeout: Long, tunneling: Boolean, cache: Boolean) -> Unit
    ): Unit = coroutineScope {
        combine(
            snapshotFlow { preferences.connectTimeout },
            snapshotFlow { preferences.tunneling },
            snapshotFlow { preferences.cache }
        ) { timeout, tunneling, cache ->
            onChanged(timeout, tunneling, cache)
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
        if (state == Player.STATE_ENDED && preferences.reconnectMode == ReconnectMode.RECONNECT) {
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
                        return@post "dynamic, seekable: unknown"
                    }
                    val seekable = currentPlayer.isCurrentMediaItemSeekable
                    val dynamic = currentPlayer.isCurrentMediaItemDynamic
                    "dynamic: $dynamic, seekable: $seekable"
                }
            }

            else -> {}
        }
    }

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
                if (iterator.hasNext()) {
                    val next = iterator.next()
                    logger.post {
                        "[${PlaybackException.getErrorCodeName(exception.errorCode)}] " +
                                "Try another mimetype, from $iterator to $next"
                    }
                    iterator = next
                    when (next) {
                        is MimetypeIterator.Trying -> tryPlay(next.mimeType)
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
        player.value?.isPlaying
        tracksGroups.value = tracks.groups
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying.value = isPlaying
    }

    override fun pauseOrContinue(value: Boolean) {
        player.value?.apply {
            if (!value) pause() else {
                playWhenReady = true
                prepare()
            }
        }
    }


    private var iterator: MimetypeIterator = MimetypeIterator.Unsupported

    private val logger = delegate.install(Profiles.SERVICE_PLAYER)
}

private fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

private sealed class MimetypeIterator {
    class Unspecified(val url: String) : MimetypeIterator()
    class Trying(val mimeType: String) : MimetypeIterator()
    object Unsupported : MimetypeIterator()

    val mimeTypeOrNull: String?
        get() = when (this) {
            is Trying -> mimeType
            else -> null
        }

    companion object {
        val ORDER_DEFAULT = arrayOf(
            MimeTypes.APPLICATION_SS,
            MimeTypes.APPLICATION_M3U8,
            MimeTypes.APPLICATION_MPD,
            MimeTypes.APPLICATION_RTSP
        )
    }

    override fun toString(): String = when (this) {
        is Trying -> "Trying[$mimeType]"
        is Unspecified -> "Unspecified[$url]"
        Unsupported -> "Unsupported"
    }

    operator fun hasNext(): Boolean = this != Unsupported

    operator fun next(): MimetypeIterator = when (this) {
        is Unspecified -> Trying(ORDER_DEFAULT.first())
        is Trying -> {
            ORDER_DEFAULT
                .getOrNull(ORDER_DEFAULT.indexOf(mimeType) + 1)
                ?.let { Trying(it) }
                ?: Unsupported
        }

        else -> throw IllegalArgumentException()
    }
}
