package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Rect
import android.net.Uri
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
import androidx.media3.exoplayer.MediaExtractorCompat
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
import androidx.media3.muxer.FragmentedMp4Muxer
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.session.MediaSession
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppFragmentedMp4Muxer
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.m3u.core.architecture.Publisher
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
import com.m3u.data.codec.Codecs
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class PlayerManagerImpl @Inject constructor(
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
    @OkhttpClient(false) private val okHttpClient: OkHttpClient,
    private val preferences: Preferences,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val cache: Cache,
    publisher: Publisher,
    delegate: Logger
) : PlayerManager, Player.Listener, MediaSession.Callback {
    private val mainCoroutineScope = CoroutineScope(mainDispatcher)
    private val ioCoroutineScope = CoroutineScope(ioDispatcher)

    private val channelPreferenceProvider = ChannelPreferenceProvider(
        directory = context.cacheDir.resolve("channel-preferences"),
        appVersion = publisher.versionCode,
        ioDispatcher = ioDispatcher
    )

    private val continueWatchingCondition = ContinueWatchingCondition.getInstance<Player>()

    override val player = MutableStateFlow<ExoPlayer?>(null)
    override val size = MutableStateFlow(Rect())

    private val mediaCommand = MutableStateFlow<MediaCommand?>(null)

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

    override val playbackState = MutableStateFlow<@Player.State Int>(Player.STATE_IDLE)
    override val playbackException = MutableStateFlow<PlaybackException?>(null)
    override val isPlaying = MutableStateFlow(false)
    override val tracksGroups = MutableStateFlow<List<Tracks.Group>>(emptyList())

    private val playbackPosition = MutableStateFlow(-1L)

    private var currentConnectTimeout = preferences.connectTimeout
    private var currentTunneling = preferences.tunneling
    private var currentCache = preferences.cache
    private var observePreferencesChangingJob: Job? = null

    init {
        mainCoroutineScope.launch {
            playbackState.collectLatest { state ->
                logger.post { "playbackState changed: $state" }
                when (state) {
                    Player.STATE_IDLE -> onPlaybackIdle()
                    Player.STATE_BUFFERING -> onPlaybackBuffering()
                    Player.STATE_READY -> onPlaybackReady()
                    Player.STATE_ENDED -> onPlaybackEnded()
                }
            }
        }
        mainCoroutineScope.launch {
            while (true) {
                ensureActive()
                playbackPosition.value = player.value?.currentPosition ?: -1L
                delay(1.seconds)
            }
        }
    }

    override suspend fun play(
        command: MediaCommand,
        applyContinueWatching: Boolean
    ) {
        logger.post { "play" }
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
            val channelPreference = getChannelPreference(channelUrl)
            val licenseType = channel.licenseType.orEmpty()
            val licenseKey = channel.licenseKey.orEmpty()

            channelRepository.reportPlayed(channel.id)

            val playlist = playlistRepository.get(channel.playlistUrl)
            val userAgent = getUserAgent(channelUrl, playlist)

            this.chain = channelPreference?.mineType
                ?.let { MimetypeChain.Remembered(channelUrl, it) }
                ?: MimetypeChain.Unspecified(channelUrl)

            logger.post { "init mimetype: $chain" }

            tryPlay(
                url = channelUrl,
                userAgent = userAgent,
                licenseType = licenseType,
                licenseKey = licenseKey,
                applyContinueWatching = applyContinueWatching
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

    private var extractor: MediaExtractorCompat? = null
    private fun tryPlay(
        url: String = channel.value?.url.orEmpty(),
        userAgent: String? = getUserAgent(channel.value?.url.orEmpty(), playlist.value),
        licenseType: String = channel.value?.licenseType.orEmpty(),
        licenseKey: String = channel.value?.licenseKey.orEmpty(),
        applyContinueWatching: Boolean
    ) {
        val rtmp: Boolean = Url(url).protocol.name == "rtmp"
        val tunneling: Boolean = preferences.tunneling

        val mimeType = when (val chain = chain) {
            is MimetypeChain.Remembered -> chain.mimeType
            is MimetypeChain.Trying -> chain.mimetype
            is MimetypeChain.Unspecified -> {
                this.chain = chain.next()
                return tryPlay(url, userAgent, licenseType, licenseKey, applyContinueWatching)
            }

            is MimetypeChain.Unsupported -> throw UnsupportedOperationException()
        }

        logger.post {
            "tryPlay, mimetype: $mimeType," +
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
        val extractorsFactory = DefaultExtractorsFactory().setTsExtractorFlags(
            FLAG_ALLOW_NON_IDR_KEYFRAMES and FLAG_DETECT_ACCESS_UNITS
        )
        extractor = MediaExtractorCompat(extractorsFactory, dataSourceFactory)
        val mediaSourceFactory = when (mimeType) {
            MimeTypes.APPLICATION_M3U8 -> HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(false)
                .setExtractorFactory(DefaultHlsExtractorFactory())

            MimeTypes.APPLICATION_SS -> ProgressiveMediaSource.Factory(
                dataSourceFactory,
                extractorsFactory
            )

            MimeTypes.APPLICATION_RTSP -> RtspMediaSource.Factory()
                .setDebugLoggingEnabled(true)
                .setForceUseRtpTcp(true)
                .setSocketFactory(SSLs.TLSTrustAll.socketFactory)

            else -> DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        }
        logger.post { "media-source-factory: ${mediaSourceFactory::class.qualifiedName}" }
        if (licenseType.isNotEmpty()) {
            val drmCallback = when {
                (licenseType in arrayOf(
                    Channel.LICENSE_TYPE_CLEAR_KEY,
                    Channel.LICENSE_TYPE_CLEAR_KEY_2
                )) && !licenseKey.startsWith("http") -> LocalMediaDrmCallback(licenseKey.toByteArray())

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
                    .setMultiSession(
                        licenseType !in arrayOf(
                            Channel.LICENSE_TYPE_CLEAR_KEY,
                            Channel.LICENSE_TYPE_CLEAR_KEY_2
                        )
                    )
                    .build(drmCallback)
                mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
            }
        }
        val player = player.updateAndGet { prev ->
            logger.post { "player instance updated" }
            prev ?: createPlayer(mediaSourceFactory, tunneling)
        }!!
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        mainCoroutineScope.launch {
            if (applyContinueWatching) {
                restoreContinueWatching(player, url)
            } else {
                cwPositionObserver.emit(-1L)
            }
        }
    }

    override suspend fun replay() {
        val prev = mediaCommand.value
        release()
        prev?.let { play(it, applyContinueWatching = false) }
    }

    override fun release() {
        logger.post { "release" }
        observePreferencesChangingJob?.cancel()
        observePreferencesChangingJob = null
        extractor = null
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
            chain = MimetypeChain.Unsupported(chain.url)
            null
        }
    }

    override fun clearCache() {
        cache.keys.forEach {
            cache.getCachedSpans(it).forEach { span ->
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
        Codecs.load().createRenderersFactory(context)
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
        logger.post { "onVideoSizeChanged, [${videoSize.toRect()}]" }
        size.value = videoSize.toRect()
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        playbackState.value = state
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
                when (val chain = chain) {
                    is MimetypeChain.Remembered -> {
                        ioCoroutineScope.launch {
                            logger.post { "onPlayerErrorChanged, parsing error! invalidate remembered mimeType!" }
                            val channelPreference = getChannelPreference(chain.url)
                            if (channelPreference != null) {
                                updateChannelPreference(
                                    chain.url,
                                    channelPreference.copy(mineType = null)
                                )
                            }
                        }
                    }

                    else -> {

                        logger.post { "onPlayerErrorChanged, parsing error! Trying another mimeType." }
                    }
                }
                if (chain.hasNext()) {
                    val next = chain.next()
                    chain = next
                    logger.post {
                        "[${PlaybackException.getErrorCodeName(exception.errorCode)}] " +
                                "Try another mimetype, from $chain to $next"
                    }
                    when (next) {
                        is MimetypeChain.Unsupported -> {
                            playbackException.value = exception
                        }

                        else -> tryPlay(applyContinueWatching = false)
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

    override fun updateSpeed(race: Float) {
        player.value?.apply {
            setPlaybackSpeed(race.coerceAtLeast(0.1f))
        }
    }

    override suspend fun recordVideo(uri: Uri) {
        withContext(mainDispatcher) {
            try {
                val currentPlayer = player.value ?: return@withContext
                val tracksGroup = currentPlayer.currentTracks.groups.first {
                    it.type == C.TRACK_TYPE_VIDEO
                } ?: return@withContext
                val formats = (0 until tracksGroup.length).mapNotNull {
                    if (!tracksGroup.isTrackSupported(it)) null
                    else tracksGroup.getTrackFormat(it)
                }
                    .mapNotNull { it.containerMimeType ?: it.sampleMimeType }
                val (mimeType, muxerFactory) = when {
                    formats.any { it in FragmentedMp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES } -> {
                        val mimeType =
                            formats.first { it in FragmentedMp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES }
                        val muxerFactory = InAppFragmentedMp4Muxer.Factory()
                        mimeType to muxerFactory
                    }

                    formats.any { it in Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES } -> {
                        val mimeType =
                            formats.first { it in Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES }
                        val muxerFactory = InAppMp4Muxer.Factory()
                        mimeType to muxerFactory
                    }

                    else -> {
                        logger.post { "recordVideo, unsupported video formats: $formats" }
                        return@withContext
                    }
                }
                val transformer = Transformer.Builder(context)
                    .setMuxerFactory(muxerFactory)
                    .setVideoMimeType(mimeType)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context.applicationContext)
                            .setEnableFallback(true)
//                        .setRequestedVideoEncoderSettings(
//                            VideoEncoderSettings.Builder()
//                                .
//                                .build()
//                        )
                            .build()
                    )
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult
                            ) {
                                super.onCompleted(composition, exportResult)
                                logger.post { "transformer, onCompleted" }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                super.onError(composition, exportResult, exportException)
                                logger.post { "transformer, onError. message=${exportException.message}, code=[${exportException.errorCode}]${exportException.errorCodeName}" }
                            }

                            override fun onFallbackApplied(
                                composition: Composition,
                                originalTransformationRequest: TransformationRequest,
                                fallbackTransformationRequest: TransformationRequest
                            ) {
                                super.onFallbackApplied(
                                    composition,
                                    originalTransformationRequest,
                                    fallbackTransformationRequest
                                )
                                logger.post { "transformer, onFallbackApplied" }
                            }
                        }
                    )
                    .build()

                withContext(mainDispatcher) {
                    transformer.start(
                        MediaItem.fromUri(channel.value?.url.orEmpty()),
                        uri.path.orEmpty()
                    )
                }
            } finally {
                logger.post { "record video completed" }
            }
        }
    }

    override val cwPositionObserver = MutableSharedFlow<Long>(replay = 1)

    override suspend fun onRewind(channelUrl: String) {
        cwPositionObserver.emit(-1L)
        resetContinueWatching(channelUrl)
        val currentPlayer = player.value ?: return
        if (currentPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
            currentPlayer.seekToDefaultPosition()
        }
    }

    private suspend fun onPlaybackIdle() {}
    private suspend fun onPlaybackBuffering() {}

    private suspend fun onPlaybackReady() {
        logger.post { "onPlaybackReady, chain=$chain" }
        when (val chain = chain) {
            is MimetypeChain.Remembered -> {
                storeContinueWatching(chain.url)
            }

            is MimetypeChain.Trying -> {
                val channelPreference = getChannelPreference(chain.url)
                updateChannelPreference(
                    chain.url,
                    channelPreference?.copy(mineType = chain.mimetype)
                        ?: ChannelPreference(mineType = chain.mimetype)
                )
                storeContinueWatching(chain.url)
            }

            else -> {}
        }
    }

    private suspend fun onPlaybackEnded() {
        if (preferences.reconnectMode == ReconnectMode.RECONNECT) {
            mainCoroutineScope.launch { replay() }
        }
        val channelUrl = chain.url
        if (channelUrl.isNotEmpty()) {
            resetContinueWatching(channelUrl)
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun storeContinueWatching(channelUrl: String) {
        logger.post { "storeContinueWatching" }
        // avoid memory leaks caused by loops
        fun checkContinueWatching(): Boolean {
            val currentPlayer = player.value ?: return false
            return continueWatchingCondition.isStoringSupported(currentPlayer)
        }
        if (!checkContinueWatching()) {
            logger.post { "storeContinueWatching, playback is not supported." }
            return
        }
        playbackPosition
            .sample(5.seconds)
            .collect { cwPosition ->
                logger.post { "storeContinueWatching, received new position: $cwPosition" }
                if (cwPosition == -1L) return@collect
                val channelPreference = getChannelPreference(channelUrl)
                updateChannelPreference(
                    channelUrl,
                    channelPreference?.copy(cwPosition = cwPosition)
                        ?: ChannelPreference(cwPosition = cwPosition)
                )
            }
    }

    private suspend fun restoreContinueWatching(player: Player, channelUrl: String) {
        val channelPreference = getChannelPreference(channelUrl)
        val cwPosition = channelPreference?.cwPosition?.takeIf { it != -1L } ?: run {
            cwPositionObserver.emit(-1L)
            return
        }
        withContext(mainDispatcher) {
            if (continueWatchingCondition.isRestoringSupported(player)) {
                logger.post { "restoreContinueWatching, $cwPosition" }
                cwPositionObserver.emit(cwPosition)
                player.seekTo(cwPosition)
            }
        }
    }

    private suspend fun resetContinueWatching(channelUrl: String) {
        logger.post { "resetContinueWatching, channelUrl=$channelUrl" }
        val channelPreference = getChannelPreference(channelUrl)
        val player = this@PlayerManagerImpl.player.value
        withContext(mainDispatcher) {
            if (player != null && continueWatchingCondition.isResettingSupported(player)) {
                updateChannelPreference(
                    channelUrl,
                    channelPreference?.copy(cwPosition = -1L) ?: ChannelPreference(cwPosition = -1L)
                )
            }
        }
    }

    private var chain: MimetypeChain = MimetypeChain.Unsupported("")

    private val logger = delegate.install(Profiles.SERVICE_PLAYER)

    /**
     * Get the kodi url options like this:
     * http://host[:port]/directory/file?a=b&c=d|option1=value1&option2=value2
     * Will get:
     * {option1=value1, option2=value2}
     *
     * https://kodi.wiki/view/HTTP
     */
    private fun String.readKodiUrlOptions(): Map<String, String?> {
        val index = this.indexOf('|')
        if (index == -1) return emptyMap()
        val options = this.drop(index + 1).split("&")
        return options
            .filter { it.isNotBlank() }
            .associate {
                val pair = it.split("=")
                val key = pair.getOrNull(0).orEmpty()
                val value = pair.getOrNull(1)
                key to value
            }
    }

    /**
     * Read user-agent appended to the channelUrl.
     * If there is no result from url, it will use playlist user-agent instead.
     */
    private fun getUserAgent(channelUrl: String, playlist: Playlist?): String? {
        val kodiUrlOptions = channelUrl.readKodiUrlOptions()
        val userAgent = kodiUrlOptions[KodiAdaptions.HTTP_OPTION_UA] ?: playlist?.userAgent
        return userAgent
    }

    private suspend fun getChannelPreference(channelUrl: String): ChannelPreference? {
        if (channelUrl.isEmpty()) return null
        return channelPreferenceProvider[channelUrl]
    }

    private suspend fun updateChannelPreference(
        channelUrl: String,
        channelPreference: ChannelPreference
    ) {
        if (channelUrl.isEmpty()) return
        channelPreferenceProvider[channelUrl] = channelPreference
    }
}

private fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

private sealed class MimetypeChain(val url: String) {
    class Remembered(
        url: String,
        val mimeType: String
    ) : MimetypeChain(url)

    class Unspecified(url: String) : MimetypeChain(url)
    class Trying(url: String, val mimetype: String) : MimetypeChain(url)
    class Unsupported(url: String) : MimetypeChain(url)

    companion object {
        val ORDERS = arrayOf(
            MimeTypes.APPLICATION_SS,
            MimeTypes.APPLICATION_M3U8,
            MimeTypes.APPLICATION_MPD,
            MimeTypes.APPLICATION_RTSP
        )
    }

    override fun toString(): String = when (this) {
        is Unspecified -> "Unspecified[$url]"
        is Trying -> "Trying[$url, $mimetype]"
        is Remembered -> "Remembered[$url, $mimeType]"
        is Unsupported -> "Unsupported[$url]"
    }

    operator fun hasNext(): Boolean = this !is Unsupported

    operator fun next(): MimetypeChain = when (this) {
        is Unspecified -> Trying(url, ORDERS.first())
        is Trying -> {
            ORDERS
                .getOrNull(ORDERS.indexOf(mimetype) + 1)
                ?.let { Trying(url, it) }
                ?: Unsupported(url)
        }

        is Remembered -> Unspecified(url)

        else -> throw IllegalArgumentException()
    }
}
