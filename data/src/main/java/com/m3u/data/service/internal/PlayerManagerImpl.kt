package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.core.net.toUri
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
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.ReconnectMode
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.get
import com.m3u.data.SSLs
import com.m3u.data.api.OkhttpClient
import com.m3u.data.api.ProviderOkhttpClient
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.copyXtreamEpisode
import com.m3u.data.database.model.copyXtreamSeries
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.providerAccountIdOrNull
import com.m3u.data.repository.provider.ProviderOperationException
import com.m3u.data.repository.provider.ProviderPlaybackCloseReason
import com.m3u.data.repository.provider.ProviderPlaybackSession
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @OkhttpClient(false) private val okHttpClient: OkHttpClient,
    @param:ProviderOkhttpClient private val providerOkHttpClient: OkHttpClient,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val subscriptionProviderRepository: SubscriptionProviderRepository,
    private val cache: Cache,
    private val settings: Settings,
    publisher: Publisher,
) : PlayerManager, MediaSession.Callback {
    private val timber = Timber.tag("PlayerManagerImpl")
    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)
    private val ioCoroutineScope = CoroutineScope(Dispatchers.IO)

    private val channelPreferenceProvider = ChannelPreferenceProvider(
        directory = context.cacheDir.resolve("channel-preferences"),
        appVersion = publisher.versionCode
    )

    private val continueWatchingCondition = ContinueWatchingCondition.getInstance<Player>()

    override val player = MutableStateFlow<ExoPlayer?>(null)
    override val size = MutableStateFlow(Rect())

    private val mediaCommand = MutableStateFlow<MediaCommand?>(null)
    private val playbackLifecycleMutex = Mutex()
    private val providerSessionState = ProviderPlaybackSessionState()
    private val providerSessionCloseQueue = ProviderSessionCloseQueue(ioCoroutineScope)
    private val playerLifecycleLock = Any()
    private var activePlayerListener: Player.Listener? = null
    private var activePlayerGeneration: Long? = null
    private var activeRequestHeaders: Map<String, String> = emptyMap()
    private var activeProviderPlayback = false
    private var activeProviderPlaybackAllowsCrossOrigin = false

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
        .onEach { timber.d("received media command: $it") }
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
    private val playbackStateEvent = MutableStateFlow(
        PlaybackStateEvent(
            generation = 0L,
            state = Player.STATE_IDLE,
        )
    )

    init {
        mainCoroutineScope.launch {
            playbackStateEvent.collectLatest { event ->
                if (!providerSessionState.isCurrent(event.generation)) return@collectLatest
                timber.d("onPlaybackStateChanged: ${event.state}")
                when (event.state) {
                    Player.STATE_IDLE -> onPlaybackIdle()
                    Player.STATE_BUFFERING -> onPlaybackBuffering()
                    Player.STATE_READY -> onPlaybackReady(event.generation)
                    Player.STATE_ENDED -> onPlaybackEnded(event.generation)
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
        val generation = providerSessionState.beginGeneration()
        playbackStateEvent.value = PlaybackStateEvent(
            generation = generation.value,
            state = Player.STATE_IDLE,
        )
        closeProviderSessionAsync(
            session = generation.detachedSession,
            reason = ProviderPlaybackCloseReason.CHANNEL_CHANGED,
        )
        playbackLifecycleMutex.withLock {
            startPlayback(
                generation = generation.value,
                command = command,
                applyContinueWatching = applyContinueWatching,
            )
        }
    }

    private suspend fun startPlayback(
        generation: Long,
        command: MediaCommand,
        applyContinueWatching: Boolean,
    ) {
        timber.d("play")
        if (!providerSessionState.isCurrent(generation)) return
        releasePlayer()
        val commandAccepted = providerSessionState.runIfCurrent(generation) {
            mediaCommand.value = command
        }
        if (!commandAccepted) return
        val channel = when (command) {
            is MediaCommand.Common -> channelRepository.get(command.channelId)
            is MediaCommand.XtreamEpisode -> channelRepository
                .get(command.channelId)
                ?.copyXtreamEpisode(command.episode)
        }
        if (!providerSessionState.isCurrent(generation)) return
        if (channel != null) {
            channel.playlistUrl.providerAccountIdOrNull()?.let { accountId ->
                providerSessionCloseQueue.awaitDrained(accountId)
                if (!providerSessionState.isCurrent(generation)) return
            }
            val providerSource = subscriptionProviderRepository.resolvePlayback(channel.id)
            if (!providerSessionState.isCurrent(generation)) {
                closeProviderSessionAsync(
                    session = providerSource?.session,
                    reason = ProviderPlaybackCloseReason.CHANNEL_CHANGED,
                )
                return
            }
            val channelUrl = providerSource?.url
                ?: channel.url.takeUnless { url -> url == Channel.URL_DYNAMIC }
                ?: throw ProviderOperationException("Dynamic playback reference was not found")
            val attachment = providerSessionState.attach(
                generation = generation,
                session = providerSource?.session,
            )
            closeProviderSessionAsync(
                session = attachment.sessionToClose,
                reason = ProviderPlaybackCloseReason.CHANNEL_CHANGED,
            )
            if (!attachment.accepted) return
            val sourceAccepted = providerSessionState.runIfCurrent(generation) {
                activeRequestHeaders = providerSource?.headers.orEmpty()
                activeProviderPlayback = providerSource != null
                activeProviderPlaybackAllowsCrossOrigin =
                    providerSource?.allowCrossOriginRequests == true
            }
            if (!sourceAccepted) return
            try {
                val channelPreference = getChannelPreference(channelUrl)
                if (!providerSessionState.isCurrent(generation)) return
                val licenseType = channel.licenseType.orEmpty()
                val licenseKey = channel.licenseKey.orEmpty()

                channelRepository.reportPlayed(channel.id)
                if (!providerSessionState.isCurrent(generation)) return

                val playlist = playlistRepository.get(channel.playlistUrl)
                if (!providerSessionState.isCurrent(generation)) return
                val userAgent = getUserAgent(channelUrl, playlist)

                val chainAccepted = providerSessionState.runIfCurrent(generation) {
                    this.chain = channelPreference?.mineType
                        ?.let { MimetypeChain.Remembered(channelUrl, it) }
                        ?: MimetypeChain.Unspecified(channelUrl)
                }
                if (!chainAccepted) return

                timber.d("init mimetype chain: ${chain::class.simpleName}")

                tryPlay(
                    generation = generation,
                    url = channelUrl,
                    userAgent = userAgent,
                    requestHeaders = activeRequestHeaders,
                    licenseType = licenseType,
                    licenseKey = licenseKey,
                    applyContinueWatching = applyContinueWatching
                )
            } catch (exception: Exception) {
                val failedSession = providerSessionState.detach(generation)
                withContext(NonCancellable) {
                    closeProviderSession(
                        session = failedSession,
                        reason = ProviderPlaybackCloseReason.PLAYBACK_FAILED,
                    )
                }
                if (providerSessionState.isCurrent(generation)) {
                    releasePlayer()
                }
                throw exception
            }
        }
    }

    private var extractor: MediaExtractorCompat? = null
    private suspend fun tryPlay(
        generation: Long,
        url: String = chain.url,
        userAgent: String? = getUserAgent(chain.url, playlist.value),
        requestHeaders: Map<String, String> = activeRequestHeaders,
        providerPlayback: Boolean = activeProviderPlayback,
        providerPlaybackAllowsCrossOrigin: Boolean = activeProviderPlaybackAllowsCrossOrigin,
        licenseType: String = channel.value?.licenseType.orEmpty(),
        licenseKey: String = channel.value?.licenseKey.orEmpty(),
        applyContinueWatching: Boolean
    ) {
        if (!providerSessionState.isCurrent(generation)) return
        val rtmp: Boolean = Url(url).protocol.name == "rtmp"
        val tunneling = settings[PreferencesKeys.TUNNELING]

        val mimeType = when (val chain = chain) {
            is MimetypeChain.Remembered -> chain.mimeType
            is MimetypeChain.Trying -> chain.mimetype
            is MimetypeChain.Unspecified -> {
                val nextAccepted = providerSessionState.runIfCurrent(generation) {
                    this.chain = chain.next()
                }
                if (!nextAccepted) return
                return tryPlay(
                    generation = generation,
                    url = url,
                    userAgent = userAgent,
                    requestHeaders = requestHeaders,
                    providerPlayback = providerPlayback,
                    providerPlaybackAllowsCrossOrigin = providerPlaybackAllowsCrossOrigin,
                    licenseType = licenseType,
                    licenseKey = licenseKey,
                    applyContinueWatching = applyContinueWatching,
                )
            }

            is MimetypeChain.Unsupported -> throw UnsupportedOperationException()
        }

        timber.d("tryPlay, mimetype: $mimeType, rtmp: $rtmp")
        val dataSourceFactory = if (rtmp) {
            RtmpDataSource.Factory()
        } else {
            createHttpDataSourceFactory(
                url = url,
                userAgent = userAgent,
                requestHeaders = requestHeaders,
                providerPlayback = providerPlayback,
                providerPlaybackAllowsCrossOrigin = providerPlaybackAllowsCrossOrigin,
            )
        }
        val extractorsFactory = DefaultExtractorsFactory().setTsExtractorFlags(
            FLAG_ALLOW_NON_IDR_KEYFRAMES and FLAG_DETECT_ACCESS_UNITS
        )
        val mediaExtractor = MediaExtractorCompat(extractorsFactory, dataSourceFactory)
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
        timber.d("media-source-factory: ${mediaSourceFactory::class.qualifiedName}")
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
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        val preparedPlayer = preparePlayer(
            generation = generation,
            mediaSourceFactory = mediaSourceFactory,
            mediaSource = mediaSource,
            mediaExtractor = mediaExtractor,
            tunneling = tunneling,
        ) ?: return
        mainCoroutineScope.launch {
            if (!providerSessionState.isCurrent(generation)) return@launch
            if (applyContinueWatching) {
                restoreContinueWatching(preparedPlayer, url)
            } else {
                cwPosition.emit(-1L)
            }
        }
    }

    override suspend fun replay() {
        val prev = mediaCommand.value
        val generation = providerSessionState.beginGeneration()
        replay(
            generation = generation,
            command = prev,
        )
    }

    private suspend fun replay(
        generation: ProviderPlaybackGeneration,
        command: MediaCommand?,
    ) {
        playbackStateEvent.value = PlaybackStateEvent(
            generation = generation.value,
            state = Player.STATE_IDLE,
        )
        closeProviderSessionAsync(
            session = generation.detachedSession,
            reason = ProviderPlaybackCloseReason.STOPPED,
        )
        playbackLifecycleMutex.withLock {
            if (command == null) {
                if (providerSessionState.isCurrent(generation.value)) {
                    releasePlayer()
                }
            } else {
                startPlayback(
                    generation = generation.value,
                    command = command,
                    applyContinueWatching = false,
                )
            }
        }
    }

    override fun release() {
        timber.d("release")
        val generation = providerSessionState.beginGeneration()
        playbackStateEvent.value = PlaybackStateEvent(
            generation = generation.value,
            state = Player.STATE_IDLE,
        )
        closeProviderSessionAsync(
            session = generation.detachedSession,
            reason = ProviderPlaybackCloseReason.STOPPED,
        )
        releasePlayer()
    }

    private fun releasePlayer() {
        synchronized(playerLifecycleLock) {
            extractor = null
            activeRequestHeaders = emptyMap()
            activeProviderPlayback = false
            activeProviderPlaybackAllowsCrossOrigin = false
            mediaCommand.value = null
            size.value = Rect()
            playbackState.value = Player.STATE_IDLE
            isPlaying.value = false
            playbackException.value = null
            tracksGroups.value = emptyList()
            chain = MimetypeChain.Unsupported("")
            player.value?.let { activePlayer ->
                activePlayerListener?.let(activePlayer::removeListener)
                activePlayer.stop()
                activePlayer.release()
            }
            activePlayerListener = null
            activePlayerGeneration = null
            player.value = null
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
        val builder = currentPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(type)
        if (type == C.TRACK_TYPE_TEXT) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
        }
        currentPlayer.trackSelectionParameters = builder.build()
    }

    override val cacheSpace: Flow<Long> = flow {
        while (true) {
            emit(cache.cacheSpace)
            delay(1.seconds)
        }
    }
        .flowOn(Dispatchers.IO)

    override suspend fun reloadThumbnail(channelUrl: String): Uri? {
        val channelPreference = getChannelPreference(channelUrl)
        return channelPreference?.thumbnail
    }

    private val thumbnailDir by lazy {
        context.cacheDir.resolve("thumbnails").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    override suspend fun syncThumbnail(channelUrl: String): Uri? = withContext(Dispatchers.IO) {
        val thumbnail = Codecs.getThumbnail(context, channelUrl.toUri()) ?: return@withContext null
        val filename = UUID.randomUUID().toString() + ".jpeg"
        val file = File(thumbnailDir, filename)
        while (!file.createNewFile()) {
            ensureActive()
            file.delete()
        }
        FileOutputStream(file).use {
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 50, it)
        }
        val uri = file.toUri()
        addChannelPreference(
            channelUrl,
            getChannelPreference(channelUrl)?.copy(
                thumbnail = uri
            ) ?: ChannelPreference(thumbnail = uri)
        )
        uri
    }

    private fun createPlayer(
        mediaSourceFactory: MediaSource.Factory,
        tunneling: Boolean,
        listener: Player.Listener,
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
            addListener(listener)
        }

    private fun preparePlayer(
        generation: Long,
        mediaSourceFactory: MediaSource.Factory,
        mediaSource: MediaSource,
        mediaExtractor: MediaExtractorCompat,
        tunneling: Boolean,
    ): ExoPlayer? {
        var result: ExoPlayer? = null
        providerSessionState.runIfCurrent(generation) {
            synchronized(playerLifecycleLock) {
                val preparedPlayer = player.value ?: run {
                    val listener = createPlayerListener(generation)
                    createPlayer(
                        mediaSourceFactory = mediaSourceFactory,
                        tunneling = tunneling,
                        listener = listener,
                    ).also { createdPlayer ->
                        timber.d("player instance updated")
                        activePlayerListener = listener
                        activePlayerGeneration = generation
                        player.value = createdPlayer
                    }
                }
                if (activePlayerGeneration == generation) {
                    extractor = mediaExtractor
                    preparedPlayer.setMediaSource(mediaSource)
                    preparedPlayer.prepare()
                    result = preparedPlayer
                }
            }
        }
        return result
    }

    private fun createPlayerListener(generation: Long): Player.Listener =
        object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                handleVideoSizeChanged(generation, videoSize)
            }

            override fun onPlaybackStateChanged(state: Int) {
                handlePlaybackStateChanged(generation, state)
            }

            override fun onPlayerErrorChanged(exception: PlaybackException?) {
                handlePlayerErrorChanged(generation, exception)
            }

            override fun onTracksChanged(tracks: Tracks) {
                handleTracksChanged(generation, tracks)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handleIsPlayingChanged(generation, isPlaying)
            }
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

    private fun createHttpDataSourceFactory(
        url: String,
        userAgent: String?,
        requestHeaders: Map<String, String>,
        providerPlayback: Boolean,
        providerPlaybackAllowsCrossOrigin: Boolean,
    ): DataSource.Factory {
        val client = if (providerPlayback) {
            providerOkHttpClient.withProviderPlaybackHeaders(
                entryUrl = url,
                headers = requestHeaders,
                allowCrossOriginRequests = providerPlaybackAllowsCrossOrigin,
            )
        } else {
            okHttpClient
        }
        val upstream = OkHttpDataSource.Factory(client)
            .setUserAgent(userAgent)
        if (!providerPlayback) {
            upstream.setDefaultRequestProperties(requestHeaders)
        }
//        return if (cache) {
//            CacheDataSource.Factory()
//                .setUpstreamDataSourceFactory(upstream)
//                .setCache(cache)
//                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
//        }
        return upstream
    }

    private fun handleVideoSizeChanged(
        generation: Long,
        videoSize: VideoSize,
    ) {
        providerSessionState.runIfCurrent(generation) {
            timber.d("onVideoSizeChanged, [${videoSize.toRect()}]")
            size.value = videoSize.toRect()
        }
    }

    private fun handlePlaybackStateChanged(
        generation: Long,
        state: Int,
    ) {
        providerSessionState.runIfCurrent(generation) {
            playbackState.value = state
            if (state == Player.STATE_ENDED) {
                closeProviderSessionAsync(
                    session = providerSessionState.detach(generation),
                    reason = ProviderPlaybackCloseReason.ENDED,
                )
            }
            playbackStateEvent.value = PlaybackStateEvent(
                generation = generation,
                state = state,
            )
        }
    }

    private fun handlePlayerErrorChanged(
        generation: Long,
        exception: PlaybackException?,
    ) {
        providerSessionState.runIfCurrent(generation) {
            when (val errorCode = exception?.errorCode) {
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    timber.w("onPlayerErrorChanged, ERROR_CODE_BEHIND_LIVE_WINDOW, trying to replay")
                    playerForGeneration(generation)?.let {
                        it.seekToDefaultPosition()
                        it.prepare()
                    }
                }

                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                    timber.w(
                        "onPlayerErrorChanged, ${PlaybackException.getErrorCodeName(errorCode)}"
                    )
                    when (val chain = chain) {
                        is MimetypeChain.Remembered -> {
                            ioCoroutineScope.launch {
                                val channelPreference = getChannelPreference(chain.url)
                                if (channelPreference != null) {
                                    addChannelPreference(
                                        chain.url,
                                        channelPreference.copy(mineType = null)
                                    )
                                }
                            }
                        }

                        else -> {}
                    }
                    if (chain.hasNext()) {
                        val next = chain.next()
                        chain = next
                        when (next) {
                            is MimetypeChain.Unsupported -> {
                                playbackException.value = exception
                                closeProviderSessionForGenerationAsync(
                                    generation = generation,
                                    reason = ProviderPlaybackCloseReason.PLAYBACK_FAILED,
                                )
                            }

                            else -> mainCoroutineScope.launch {
                                tryPlay(
                                    generation = generation,
                                    applyContinueWatching = false,
                                )
                            }
                        }
                    }
                }

                PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                    playbackException.value = exception
                    closeProviderSessionForGenerationAsync(
                        generation = generation,
                        reason = ProviderPlaybackCloseReason.PLAYBACK_FAILED,
                    )
                }

                else -> {
                    if (exception != null) {
                        timber.e(exception, PlaybackException.getErrorCodeName(exception.errorCode))
                        closeProviderSessionForGenerationAsync(
                            generation = generation,
                            reason = ProviderPlaybackCloseReason.PLAYBACK_FAILED,
                        )
                    }
                    playbackException.value = exception
                }
            }
        }
    }

    private fun handleTracksChanged(
        generation: Long,
        tracks: Tracks,
    ) {
        providerSessionState.runIfCurrent(generation) {
            tracksGroups.value = tracks.groups
        }
    }

    private fun handleIsPlayingChanged(
        generation: Long,
        isPlaying: Boolean,
    ) {
        providerSessionState.runIfCurrent(generation) {
            this.isPlaying.value = isPlaying
        }
    }

    private fun playerForGeneration(generation: Long): ExoPlayer? =
        synchronized(playerLifecycleLock) {
            player.value.takeIf { activePlayerGeneration == generation }
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
        withContext(Dispatchers.Main) {
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
                        timber.e("Failed to record frame, Unsupported video formats: $formats")
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
                                timber.d("transformer, onCompleted")
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                super.onError(composition, exportResult, exportException)
                                timber.e(exportException, "transformer, onError")
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
                            }
                        }
                    )
                    .build()

                withContext(Dispatchers.Main) {
                    transformer.start(
                        MediaItem.fromUri(chain.url),
                        uri.path.orEmpty()
                    )
                }
            } finally {
                timber.d("Record frame completed")
            }
        }
    }

    override val cwPosition = MutableSharedFlow<Long>(replay = 1)

    override suspend fun onResetPlayback(channelUrl: String) {
        cwPosition.emit(-1L)
        resetContinueWatching(channelUrl, ignorePositionCondition = true)
        val currentPlayer = player.value ?: return
        if (currentPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
            currentPlayer.seekToDefaultPosition()
        }
    }

    override suspend fun getCwPosition(channelUrl: String): Long {
        val channelPreference = getChannelPreference(channelUrl)
        return channelPreference?.cwPosition ?: -1L
    }

    private suspend fun onPlaybackIdle() {}
    private suspend fun onPlaybackBuffering() {}

    private suspend fun onPlaybackReady(generation: Long) {
        if (!providerSessionState.isCurrent(generation)) return
        timber.d("onPlaybackReady, trying the playChain $chain")
        when (val chain = chain) {
            is MimetypeChain.Remembered -> {
                storeContinueWatching(chain.url)
            }

            is MimetypeChain.Trying -> {
                val channelPreference = getChannelPreference(chain.url)
                if (!providerSessionState.isCurrent(generation)) return
                addChannelPreference(
                    chain.url,
                    channelPreference?.copy(mineType = chain.mimetype)
                        ?: ChannelPreference(mineType = chain.mimetype)
                )
                storeContinueWatching(chain.url)
            }

            else -> {}
        }
    }

    private suspend fun onPlaybackEnded(generation: Long) {
        if (!providerSessionState.isCurrent(generation)) return
        val channelUrl = chain.url
        if (
            settings[PreferencesKeys.RECONNECT_MODE] == ReconnectMode.RECONNECT &&
            providerSessionState.isCurrent(generation)
        ) {
            val command = mediaCommand.value
            mainCoroutineScope.launch {
                val replayGeneration = providerSessionState.beginGenerationIfCurrent(generation)
                    ?: return@launch
                replay(
                    generation = replayGeneration,
                    command = command,
                )
            }
        }
        if (
            channelUrl.isNotEmpty() &&
            providerSessionState.isCurrent(generation)
        ) {
            resetContinueWatching(channelUrl)
        }
    }

    private fun closeProviderSessionForGenerationAsync(
        generation: Long,
        reason: ProviderPlaybackCloseReason,
    ) {
        closeProviderSessionAsync(
            session = providerSessionState.detach(generation),
            reason = reason,
        )
    }

    private fun closeProviderSessionAsync(
        session: ProviderPlaybackSession?,
        reason: ProviderPlaybackCloseReason,
    ) {
        if (session == null) return
        providerSessionCloseQueue.enqueue(session.accountId) {
            closeProviderSession(session, reason)
        }
    }

    private suspend fun closeProviderSession(
        session: ProviderPlaybackSession?,
        reason: ProviderPlaybackCloseReason,
    ) {
        if (session == null) return
        runCatching {
            subscriptionProviderRepository.closePlayback(session, reason)
        }.onFailure { exception ->
            timber.w(exception, "Failed to close provider playback session")
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun storeContinueWatching(channelUrl: String) {
        timber.d("start storeContinueWatching")
        // avoid memory leaks caused by loops
        fun checkContinueWatching(): Boolean {
            val currentPlayer = player.value ?: return false
            return continueWatchingCondition.isStoringSupported(currentPlayer)
        }
        if (!checkContinueWatching()) {
            timber.w("failed to storeContinueWatching, playback is not supported.")
            return
        }
        playbackPosition
            .sample(5.seconds)
            .collect { newCwPosition ->
                timber.d("storeContinueWatching, received new position: $newCwPosition")
                if (newCwPosition == -1L) return@collect
                val channelPreference = getChannelPreference(channelUrl)
                addChannelPreference(
                    channelUrl,
                    channelPreference?.copy(cwPosition = newCwPosition)
                        ?: ChannelPreference(cwPosition = newCwPosition)
                )
            }
    }

    private suspend fun restoreContinueWatching(player: Player, channelUrl: String) {
        val channelPreference = getChannelPreference(channelUrl)
        val cachedCwPosition = channelPreference?.cwPosition?.takeIf { it != -1L } ?: run {
            cwPosition.emit(-1L)
            return
        }
        withContext(Dispatchers.Main) {
            if (continueWatchingCondition.isRestoringSupported(player)) {
                timber.d("restoreContinueWatching, $cachedCwPosition")
                cwPosition.emit(cachedCwPosition)
                player.seekTo(cachedCwPosition)
            }
        }
    }

    private suspend fun resetContinueWatching(
        channelUrl: String,
        ignorePositionCondition: Boolean = false
    ) {
        timber.d("resetContinueWatching, channelUrl=$channelUrl, ignorePositionCondition=$ignorePositionCondition")
        val channelPreference = getChannelPreference(channelUrl)
        val player = this@PlayerManagerImpl.player.value
        withContext(Dispatchers.Main) {
            if (player != null && continueWatchingCondition.isResettingSupported(
                    player,
                    ignorePositionCondition
                )
            ) {
                addChannelPreference(
                    channelUrl,
                    channelPreference?.copy(cwPosition = -1L) ?: ChannelPreference(cwPosition = -1L)
                )
            }
        }
    }

    private var chain: MimetypeChain = MimetypeChain.Unsupported("")

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

    private suspend fun addChannelPreference(
        channelUrl: String,
        channelPreference: ChannelPreference
    ) {
        if (channelUrl.isEmpty()) return
        channelPreferenceProvider[channelUrl] = channelPreference
    }
}

fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

private data class PlaybackStateEvent(
    val generation: Long,
    val state: @Player.State Int,
)

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
