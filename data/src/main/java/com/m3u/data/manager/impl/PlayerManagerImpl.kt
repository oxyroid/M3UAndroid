@file:OptIn(UnstableApi::class)

package com.m3u.data.manager.impl

import android.content.Context
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamDao: StreamDao,
    private val pref: Pref
) : PlayerManager, Player.Listener, MediaSession.Callback {
    private val _player = MutableStateFlow<Player?>(null)
    override val player: Flow<Player?> = _player.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main) + Job()

    private val _streamId = MutableStateFlow<Int?>(null)
    override val streamId: StateFlow<Int?> = _streamId.asStateFlow()

    private val _videoSize = MutableStateFlow(Rect())
    override val videoSize: StateFlow<Rect> = _videoSize.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    override val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _playbackError = MutableStateFlow<PlaybackException?>(null)
    override val playerError: StateFlow<PlaybackException?> = _playbackError.asStateFlow()

    private val isSSLVerification = pref
        .observeAsFlow { it.isSSLVerification }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Pref.DEFAULT_SSL_VERIFICATION
        )
    private val connectTimeout = pref
        .observeAsFlow { it.connectTimeout }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Pref.DEFAULT_CONNECT_TIMEOUT
        )

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        combine(
            isSSLVerification,
            connectTimeout
        ) { _, _ -> replay() }
            .launchIn(scope)
    }

    private fun createPlayer(
        isSSLVerification: Boolean,
        timeout: Long
    ): Player {
        val rf = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }
        val msf = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(
                if (isSSLVerification) DefaultDataSource.Factory(context)
                else DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(
                        OkHttpClient.Builder()
                            .sslSocketFactory(SSL.TLSTrustAll.socketFactory, Certs.TrustAll)
                            .hostnameVerifier { _, _ -> true }
                            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                            .callTimeout(timeout, TimeUnit.MILLISECONDS)
                            .readTimeout(timeout, TimeUnit.MILLISECONDS)
                            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                            .build()
                    )
                )
            )
            .apply {
                drmSessionManager?.let { manager ->
                    setDrmSessionManagerProvider { manager }
                }
            }
        val ts = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
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
            }
    }

    private var drmSessionManager: DrmSessionManager? = null

    override fun play(streamId: Int) {
        val prev = _player.value
        if (prev != null) stop()
        _streamId.value = streamId

        coroutineScope.launch {
            val stream = streamDao.get(streamId) ?: return@launch
            val useDrm = stream.licenseType != null && stream.licenseKey != null

            drmSessionManager = if (useDrm) {
                val licenseType = stream.licenseType!!
                val licenseKey = stream.licenseKey!!
                val callback = LocalMediaDrmCallback(licenseKey.toByteArray())
                val uuid = when (stream.licenseType) {
                    Stream.LICENSE_TYPE_WIDEVINE -> C.WIDEVINE_UUID
                    Stream.LICENSE_TYPE_CLEAR_KEY -> C.CLEARKEY_UUID
                    Stream.LICENSE_TYPE_PLAY_READY -> C.PLAYREADY_UUID
                    else -> C.CLEARKEY_UUID
                }
                val mediaDrm = FrameworkMediaDrm.newInstance(uuid)
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(uuid) { mediaDrm }
                    .build(callback)
            } else null

            withContext(Dispatchers.Main) {
                _player.value = createPlayer(isSSLVerification.value, connectTimeout.value).also {
                    it.addListener(this@PlayerManagerImpl)
                    val url = stream.url
                    val mediaItem = MediaItem.fromUri(url)
                    it.setMediaItem(mediaItem)
                    it.prepare()
                }
            }
        }
    }

    override fun stop() {
        _streamId.value = null
        _player.update {
            it?.removeListener(this)
            it?.stop()
            it?.release()
            null
        }
        _groups.value = emptyList()
        _selected.value = emptyMap()
        _playbackState.value = Player.STATE_IDLE
        _playbackError.value = null
        _videoSize.value = Rect()
    }

    override fun replay() {
        streamId.value?.let { play(it) }
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
        if (pref.reconnectMode != ReconnectMode.NO || error?.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            _player.value?.let {
                it.seekToDefaultPosition()
                it.prepare()
            }
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
}

private fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}
