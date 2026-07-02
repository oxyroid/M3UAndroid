package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
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
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
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
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.ReconnectMode
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.flowOf
import com.m3u.core.architecture.preferences.get
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
import com.m3u.data.util.StreamUrlOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @OkhttpClient(false) private val okHttpClient: OkHttpClient,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val cache: Cache,
    private val settings: Settings,
    private val wifiManager: WifiManager,
    publisher: Publisher,
) : PlayerManager, Player.Listener, MediaSession.Callback {
    private val timber = Timber.tag("PlayerManagerImpl")
    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)
    private val ioCoroutineScope = CoroutineScope(Dispatchers.IO)

    private val channelPreferenceProvider = ChannelPreferenceProvider(
        directory = context.cacheDir.resolve("channel-preferences"),
        appVersion = publisher.versionCode
    )

    private val continueWatchingCondition = ContinueWatchingCondition.getInstance<Player>()
    private val nightAudioEffect = NightAudioEffect()

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
    override val streamMetadata = MutableStateFlow<String?>(null)
    override val tracksGroups = MutableStateFlow<List<Tracks.Group>>(emptyList())

    private val playbackPosition = MutableStateFlow(-1L)

    init {
        mainCoroutineScope.launch {
            playbackState.collectLatest { state ->
                timber.d("onPlaybackStateChanged: $state")
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
        mainCoroutineScope.launch {
            settings.flowOf(PreferencesKeys.NIGHT_AUDIO_MODE).collectLatest { enabled ->
                nightAudioEffect.setEnabled(enabled)
            }
        }
    }

    override suspend fun play(
        command: MediaCommand,
        applyContinueWatching: Boolean
    ) {
        timber.d("play")
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
            updateMulticastLock(channelUrl)
            val channelPreference = getChannelPreference(channelUrl)
            val licenseType = channel.licenseType.orEmpty()
            val licenseKey = channel.licenseKey.orEmpty()

            channelRepository.reportPlayed(channel.id)

            val playlist = playlistRepository.get(channel.playlistUrl)
            val userAgent = getUserAgent(channelUrl, playlist)
            val requestHeaders = getRequestHeaders(channelUrl)
            val playbackProtocol = StreamUrlOptions.stripFromUrl(channelUrl).protocolName()

            this.chain = if (playbackProtocol == "udp" || playbackProtocol == "rtp") {
                MimetypeChain.Remembered(channelUrl, UDP_MPEG_TS_MIME_TYPE)
            } else {
                channelPreference?.mineType
                    ?.let { MimetypeChain.Remembered(channelUrl, it) }
                    ?: MimetypeChain.Unspecified(channelUrl)
            }

            timber.d("init mimetype: $chain")

            tryPlay(
                url = channelUrl,
                userAgent = userAgent,
                requestHeaders = requestHeaders,
                licenseType = licenseType,
                licenseKey = licenseKey,
                applyContinueWatching = applyContinueWatching
            )
        }
    }

    private var extractor: MediaExtractorCompat? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private suspend fun tryPlay(
        url: String = channel.value?.url.orEmpty(),
        userAgent: String? = getUserAgent(channel.value?.url.orEmpty(), playlist.value),
        requestHeaders: Map<String, String> = getRequestHeaders(channel.value?.url.orEmpty()),
        licenseType: String = channel.value?.licenseType.orEmpty(),
        licenseKey: String = channel.value?.licenseKey.orEmpty(),
        applyContinueWatching: Boolean
    ) {
        val playbackUrl = StreamUrlOptions.stripFromUrl(url)
        val streamOptions = StreamUrlOptions.readFromUrl(url)
        val separateVideoUrl = streamOptions[StreamUrlOptions.VIDEO_URL]
            ?.takeIf { it.isNotBlank() }
        val playbackProtocol = playbackUrl.protocolName()
        val rtmp = playbackProtocol == "rtmp"
        val udpLike = playbackProtocol == "udp" || playbackProtocol == "rtp"
        val transportUrl = playbackUrl.toUdpTransportUrlIfRtp()
        val tunneling = settings[PreferencesKeys.TUNNELING]

        val mimeType = when (val chain = chain) {
            is MimetypeChain.Remembered -> chain.mimeType
            is MimetypeChain.Trying -> chain.mimetype
            is MimetypeChain.Unspecified -> {
                this.chain = chain.next()
                return tryPlay(url, userAgent, requestHeaders, licenseType, licenseKey, applyContinueWatching)
            }

            is MimetypeChain.Unsupported -> throw UnsupportedOperationException()
        }

        timber.d("tryPlay, mimetype: $mimeType, url: $playbackUrl, user-agent: $userAgent, protocol: $playbackProtocol")
        val dataSourceFactory = when {
            rtmp -> RtmpDataSource.Factory()
            udpLike -> DefaultDataSource.Factory(context)
            else -> createHttpDataSourceFactory(userAgent, requestHeaders)
        }
        val extractorsFactory = DefaultExtractorsFactory().setTsExtractorFlags(
            FLAG_ALLOW_NON_IDR_KEYFRAMES or FLAG_DETECT_ACCESS_UNITS
        )
        extractor = MediaExtractorCompat(extractorsFactory, dataSourceFactory)
        val mediaSourceFactory = createMediaSourceFactory(
            mimeType = mimeType,
            dataSourceFactory = dataSourceFactory,
            extractorsFactory = extractorsFactory,
            udpLike = udpLike
        )
        timber.d("media-source-factory: ${mediaSourceFactory::class.qualifiedName}")
        if (licenseType.isNotEmpty()) {
            val drmCallback = when {
                (licenseType in arrayOf(
                    Channel.LICENSE_TYPE_CLEAR_KEY,
                    Channel.LICENSE_TYPE_CLEAR_KEY_2
                )) && !licenseKey.startsWith("http") ->
                    LocalMediaDrmCallback(ClearKeyLicense.normalize(licenseKey).toByteArray())

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
            timber.d("player instance updated")
            prev ?: createPlayer(mediaSourceFactory, tunneling)
        }!!
        val mediaItem = buildMediaItem(transportUrl)
        val audioSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        val mediaSource: MediaSource = separateVideoUrl
            ?.let {
                createSeparateVideoMediaSource(
                    url = it,
                    userAgent = userAgent,
                    requestHeaders = requestHeaders,
                    extractorsFactory = extractorsFactory
                )
            }
            ?.let { videoSource -> MergingMediaSource(true, audioSource, videoSource) }
            ?: audioSource
        player.setMediaSource(mediaSource)
        player.prepare()
        mainCoroutineScope.launch {
            if (applyContinueWatching) {
                restoreContinueWatching(player, url)
            } else {
                cwPosition.emit(-1L)
            }
        }
    }

    override suspend fun replay() {
        val prev = mediaCommand.value
        release()
        prev?.let { play(it, applyContinueWatching = false) }
    }

    private fun buildMediaItem(playbackUrl: String): MediaItem {
        val currentChannel = channel.value
        val metadata = MediaMetadata.Builder()
            .setTitle(currentChannel?.title)
            .setDisplayTitle(currentChannel?.title)
            .setArtist(currentChannel?.category)
            .setArtworkUri(currentChannel?.cover?.toUri())
            .build()
        return MediaItem.Builder()
            .setUri(playbackUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun createSeparateVideoMediaSource(
        url: String,
        userAgent: String?,
        requestHeaders: Map<String, String>,
        extractorsFactory: DefaultExtractorsFactory
    ): MediaSource {
        val playbackUrl = StreamUrlOptions.stripFromUrl(url)
        val playbackProtocol = playbackUrl.protocolName()
        val rtmp = playbackProtocol == "rtmp"
        val udpLike = playbackProtocol == "udp" || playbackProtocol == "rtp"
        val dataSourceFactory = when {
            rtmp -> RtmpDataSource.Factory()
            udpLike -> DefaultDataSource.Factory(context)
            else -> createHttpDataSourceFactory(userAgent, requestHeaders)
        }
        val mimeType = when (playbackProtocol) {
            "rtsp" -> MimeTypes.APPLICATION_RTSP
            else -> null
        }
        val mediaSourceFactory = createMediaSourceFactory(
            mimeType = mimeType,
            dataSourceFactory = dataSourceFactory,
            extractorsFactory = extractorsFactory,
            udpLike = udpLike
        )
        return mediaSourceFactory.createMediaSource(MediaItem.fromUri(playbackUrl.toUdpTransportUrlIfRtp()))
    }

    private fun createMediaSourceFactory(
        mimeType: String?,
        dataSourceFactory: DataSource.Factory,
        extractorsFactory: DefaultExtractorsFactory,
        udpLike: Boolean
    ): MediaSource.Factory {
        return if (udpLike) {
            ProgressiveMediaSource.Factory(
                dataSourceFactory,
                extractorsFactory
            )
        } else {
            when (mimeType) {
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
        }
    }

    override fun release() {
        timber.d("release")
        extractor = null
        releaseMulticastLock()
        player.update {
            it ?: return
            it.stop()
            nightAudioEffect.release()
            it.removeListener(this)
            it.release()
            mediaCommand.value = null
            size.value = Rect()
            playbackState.value = Player.STATE_IDLE
            playbackException.value = null
            streamMetadata.value = null
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
        chooseTrack(group, listOf(index))
    }

    override fun chooseTrack(groupIndex: Int, trackIndex: Int) {
        val group = tracksGroups.value.getOrNull(groupIndex)?.mediaTrackGroup ?: return
        chooseTrack(group, listOf(trackIndex))
    }

    private fun chooseTrack(group: TrackGroup, indices: List<Int>) {
        val currentPlayer = player.value ?: return
        val type = group.type
        val override = TrackSelectionOverride(group, indices)
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

    private fun updateMulticastLock(url: String) {
        if (!StreamUrlOptions.stripFromUrl(url).isMulticastTransportUrl()) {
            releaseMulticastLock()
            return
        }
        if (multicastLock?.isHeld == true) return
        multicastLock = runCatching {
            wifiManager.createMulticastLock("m3u-player-multicast").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onSuccess {
            timber.d("player multicast lock acquired")
        }.onFailure { error ->
            timber.w(error, "failed to acquire player multicast lock")
        }.getOrNull()
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        multicastLock = null
        runCatching {
            if (lock.isHeld) lock.release()
        }.onSuccess {
            timber.d("player multicast lock released")
        }.onFailure { error ->
            timber.w(error, "failed to release player multicast lock")
        }
    }

    private fun createHttpDataSourceFactory(
        userAgent: String?,
        requestHeaders: Map<String, String>
    ): DataSource.Factory {
        val upstream = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(requestHeaders)
//        return if (cache) {
//            CacheDataSource.Factory()
//                .setUpstreamDataSourceFactory(upstream)
//                .setCache(cache)
//                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
//        }
        return upstream
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        timber.d("onVideoSizeChanged, [${videoSize.toRect()}]")
        size.value = videoSize.toRect()
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        playbackState.value = state
    }

    override fun onPlayerErrorChanged(exception: PlaybackException?) {
        super.onPlayerErrorChanged(exception)
        when (val errorCode = exception?.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                timber.w("onPlayerErrorChanged, ERROR_CODE_BEHIND_LIVE_WINDOW, trying to replay")
                player.value?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
            }

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                timber.w("onPlayerErrorChanged, ${PlaybackException.getErrorCodeName(errorCode)}")
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
                        }

                        else -> mainCoroutineScope.launch { tryPlay(applyContinueWatching = false) }
                    }
                }
            }

            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {}

            else -> {
                if (exception != null) {
                    timber.e(exception, PlaybackException.getErrorCodeName(exception.errorCode))
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

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        super.onAudioSessionIdChanged(audioSessionId)
        nightAudioEffect.onAudioSessionIdChanged(audioSessionId)
    }

    override fun onMetadata(metadata: Metadata) {
        super.onMetadata(metadata)
        val currentStreamMetadata = (0 until metadata.length())
            .asSequence()
            .map { index -> metadata[index] }
            .mapNotNull { entry ->
                when (entry) {
                    is IcyInfo -> entry.title
                        ?.takeIf { it.isNotBlank() }
                        ?: entry.url?.takeIf { it.isNotBlank() }

                    else -> null
                }
            }
            .firstOrNull()
        if (currentStreamMetadata != null) {
            streamMetadata.value = currentStreamMetadata
        }
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
        val outputFile = withContext(Dispatchers.IO) {
            context.cacheDir
                .resolve("records")
                .apply { mkdirs() }
                .let { directory -> File.createTempFile("record-", ".mp4", directory) }
        }
        try {
            val recorded = withContext(Dispatchers.Main) {
                val currentPlayer = player.value ?: return@withContext false
                val sourceUrl = channel.value?.url
                    ?.substringBefore('|')
                    ?.takeIf { it.isNotBlank() }
                    ?: return@withContext false
                val tracksGroup = currentPlayer.currentTracks.groups.firstOrNull {
                    it.type == C.TRACK_TYPE_VIDEO
                } ?: return@withContext false
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
                        return@withContext false
                    }
                }
                suspendCancellableCoroutine { continuation ->
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
                                    if (continuation.isActive) {
                                        continuation.resume(Unit)
                                    }
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    super.onError(composition, exportResult, exportException)
                                    timber.e(exportException, "transformer, onError")
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(exportException)
                                    }
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

                    continuation.invokeOnCancellation {
                        transformer.cancel()
                    }
                    transformer.start(
                        MediaItem.fromUri(sourceUrl),
                        outputFile.absolutePath
                    )
                }
                true
            }
            if (!recorded) return
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: error("Failed to open record output stream: $uri")
            }
        } finally {
            withContext(Dispatchers.IO) {
                outputFile.delete()
            }
            timber.d("Record frame completed")
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

    private suspend fun onPlaybackReady() {
        timber.d("onPlaybackReady, trying the playChain $chain")
        when (val chain = chain) {
            is MimetypeChain.Remembered -> {
                storeContinueWatching(chain.url)
            }

            is MimetypeChain.Trying -> {
                val channelPreference = getChannelPreference(chain.url)
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

    private suspend fun onPlaybackEnded() {
        if (settings[PreferencesKeys.RECONNECT_MODE] == ReconnectMode.RECONNECT) {
            mainCoroutineScope.launch { replay() }
        }
        val channelUrl = chain.url
        if (channelUrl.isNotEmpty()) {
            resetContinueWatching(channelUrl)
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
    /**
     * Read user-agent appended to the channelUrl.
     * If there is no result from url, it will use playlist user-agent instead.
     */
    private fun getUserAgent(channelUrl: String, playlist: Playlist?): String? {
        val kodiUrlOptions = StreamUrlOptions.readFromUrl(channelUrl)
        return kodiUrlOptions[StreamUrlOptions.USER_AGENT] ?: playlist?.userAgent
    }

    private fun getRequestHeaders(channelUrl: String): Map<String, String> {
        val kodiUrlOptions = StreamUrlOptions.readFromUrl(channelUrl)
        return buildMap {
            kodiUrlOptions[StreamUrlOptions.REFERER]
                ?.takeIf { it.isNotBlank() }
                ?.let { put("Referer", it) }
            kodiUrlOptions[StreamUrlOptions.ORIGIN]
                ?.takeIf { it.isNotBlank() }
                ?.let { put("Origin", it) }
            kodiUrlOptions[StreamUrlOptions.COOKIE]
                ?.takeIf { it.isNotBlank() }
                ?.let { put("Cookie", it) }
        }
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

private const val UDP_MPEG_TS_MIME_TYPE = "video/mp2t"

private fun String.protocolName(): String =
    runCatching { Url(this).protocol.name.lowercase() }.getOrDefault("")

private fun String.toUdpTransportUrlIfRtp(): String =
    if (startsWith("rtp://", ignoreCase = true)) {
        "udp://${substringAfter("://")}"
    } else {
        this
    }

private fun String.isMulticastTransportUrl(): Boolean {
    val protocol = protocolName()
    if (protocol != "udp" && protocol != "rtp") return false
    val host = Uri.parse(toUdpTransportUrlIfRtp()).host
        ?.trimStart('@')
        ?.takeIf { it.isNotBlank() }
        ?: return false
    return runCatching { InetAddress.getByName(host).isMulticastAddress }.getOrDefault(false)
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
