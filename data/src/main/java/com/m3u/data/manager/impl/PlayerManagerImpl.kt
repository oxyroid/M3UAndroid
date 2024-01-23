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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.annotation.ReconnectMode
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.Certs
import com.m3u.data.SSL
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.model.Stream
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamDao: StreamDao,
    private val pref: Pref
) : PlayerManager, Player.Listener, MediaSession.Callback {
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    override val player: Flow<Player?> = _player.asStateFlow()

    private val _streamId = MutableStateFlow<Int?>(null)
    override val streamId: StateFlow<Int?> = _streamId.asStateFlow()

    private val _stream = MutableStateFlow<Stream?>(null)

    private val currentMimeType = MutableStateFlow<String?>(null)

    private val _videoSize = MutableStateFlow(Rect())
    override val videoSize: StateFlow<Rect> = _videoSize.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    override val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _playbackError = MutableStateFlow<PlaybackException?>(null)
    override val playerError: StateFlow<PlaybackException?> = _playbackError.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        combine(
            pref.observeAsFlow { it.isSSLVerification },
            pref.observeAsFlow { it.connectTimeout },
            pref.observeAsFlow { it.tunneling }
        ) { _, _, _ -> replay() }
            .launchIn(coroutineScope)
    }

    private var licenseKey: String? = null
    private fun createPlayer(
        isSSLVerification: Boolean,
        timeout: Long,
        tunneling: Boolean
    ): ExoPlayer {
        val rf = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }
        val dsf = DefaultDataSource.Factory(
            context,
            buildHttpDataSourceFactory(!isSSLVerification, timeout)
        )
        val msf = DefaultMediaSourceFactory(dsf)
            .setDrmSessionManagerProvider {
                val stream = _stream.value
                val licenseKey = stream?.licenseKey
                val licenseType = stream?.licenseType
                if (licenseKey != null && licenseType != null) {
                    val callback = HttpMediaDrmCallback(licenseKey, dsf)
                    val uuid = getUUID(licenseType)
                    DefaultDrmSessionManager.Builder()
                        .setMultiSession(false)
                        .setUuidAndExoMediaDrmProvider(uuid) {
                            FrameworkMediaDrm.newInstance(uuid)
                        }
                        .build(callback)
                } else {
                    DrmSessionManager.DRM_UNSUPPORTED
                }
            }

        val ts = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSizeSd()
                    .setTunnelingEnabled(tunneling)
            )
        }

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(msf)
            .setRenderersFactory(rf)
            .setTrackSelector(ts)
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

    private fun getUUID(type: String): UUID {
        return when (type) {
            Stream.LICENSE_TYPE_CLEAR_KEY -> C.CLEARKEY_UUID
            Stream.LICENSE_TYPE_WIDEVINE -> C.WIDEVINE_UUID
            Stream.LICENSE_TYPE_PLAY_READY -> C.PLAYREADY_UUID
            else -> C.CLEARKEY_UUID
        }
    }

    override suspend fun play(streamId: Int) {
        val stream = withContext(Dispatchers.IO) { streamDao.get(streamId) } ?: return
        _stream.value = stream
        _streamId.value = streamId
        licenseKey = stream.licenseKey
        tryPlay(
            url = stream.url,
            mimeType = null
        )
    }

    private fun tryPlay(
        url: String,
        mimeType: String?
    ) {
        val currentPlayer = _player.value ?: createPlayer(
            isSSLVerification = pref.isSSLVerification,
            timeout = pref.connectTimeout,
            tunneling = pref.tunneling
        ).also {
            _player.value.also { prev ->
                prev?.stop()
                prev?.release()
                prev?.removeListener(this)
            }
            _player.value = it
        }

        when (mimeType) {
            MimeTypes.APPLICATION_SS -> {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))

                currentPlayer.setMediaSource(mediaSource)
            }

            MimeTypes.APPLICATION_RTSP -> {
                val mediaSource = RtspMediaSource.Factory()
                    .createMediaSource(MediaItem.fromUri(url))
                currentPlayer.setMediaSource(mediaSource)
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

    override suspend fun stop() {
        _player.update {
            it?.stop()
            it?.release()
            it?.removeListener(this)
            null
        }
        _streamId.value = null
        _stream.value = null
        _groups.value = emptyList()
        _selected.value = emptyMap()
        _videoSize.value = Rect()
        _playbackError.value = null
        _playbackState.value = Player.STATE_IDLE
        currentMimeType.value = null
    }

    override suspend fun replay() {
        val streamId = streamId.value ?: return
        stop()
        play(streamId)
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
                if (pref.reconnectMode != ReconnectMode.NO) {
                    _player.value?.let {
                        it.seekToDefaultPosition()
                        it.prepare()
                    }
                }
            }

            in 3000..3999 -> {
                val retryMimetype = when (currentMimeType.value) {
                    null -> MimeTypes.APPLICATION_M3U8
                    MimeTypes.APPLICATION_M3U8 -> MimeTypes.APPLICATION_MPD
                    MimeTypes.APPLICATION_MPD -> MimeTypes.APPLICATION_SS
                    MimeTypes.APPLICATION_SS -> MimeTypes.APPLICATION_RTSP
                    MimeTypes.APPLICATION_RTSP -> null
                    else -> null
                }.also { currentMimeType.value = it }

                if (retryMimetype != null) {
                    _stream.value?.let { stream ->
                        licenseKey = stream.licenseKey
                        tryPlay(
                            url = stream.url,
                            mimeType = retryMimetype
                        )
                    }
                }
            }

            else -> {}
        }
        _playbackError.value = error
    }

    private val _groups = MutableStateFlow<List<Tracks.Group>>(emptyList())
    override val groups: StateFlow<List<Tracks.Group>> = _groups.asStateFlow()

    private val _selected = MutableStateFlow<Map<@C.TrackType Int, Format?>>(emptyMap())
    override val selected: StateFlow<Map<@C.TrackType Int, Format?>> = _selected.asStateFlow()

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        _groups.value = tracks.groups
        _selected.value = tracks.groups
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

    override fun chooseTrack(group: TrackGroup, trackIndex: Int) {
        val currentPlayer = _player.value ?: return
        val override = TrackSelectionOverride(group, trackIndex)
        currentPlayer.trackSelectionParameters = currentPlayer
            .trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }

    private fun buildHttpDataSourceFactory(
        trustAll: Boolean,
        timeout: Long
    ): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(
            OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .callTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .apply {
                    if (trustAll) {
                        sslSocketFactory(SSL.TLSTrustAll.socketFactory, Certs.TrustAll)
                            .hostnameVerifier { _, _ -> true }
                    }
                }
                .build()
        )
    }
}

private fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}
