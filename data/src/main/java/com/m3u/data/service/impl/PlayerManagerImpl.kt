@file:OptIn(UnstableApi::class)

package com.m3u.data.service.impl

import android.content.Context
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.contract.Certs
import com.m3u.data.contract.SSL
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private data class PlayerPayload(
    val isSSLVerification: Boolean,
    val timeout: Long
)

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pref: Pref
) : PlayerManager(), Player.Listener, MediaSession.Callback {
    private val player = MutableStateFlow<Player?>(null)

    override fun observe(): Flow<Player?> = player.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main) + Job()

    private val _url = MutableStateFlow<String?>(null)
    override val url: StateFlow<String?> = _url.asStateFlow()

    private val payload = pref
        .observeAsFlow { PlayerPayload(it.isSSLVerification, it.connectTimeout) }
        .onEach { payload ->
            player.update {
                createPlayer(payload)
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerPayload(
                pref.isSSLVerification,
                pref.connectTimeout
            )
        )

    private fun createPlayer(payload: PlayerPayload): Player {
        val rf = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }
        val msf = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(
                if (payload.isSSLVerification) DefaultDataSource.Factory(context)
                else DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(
                        OkHttpClient.Builder()
                            .sslSocketFactory(SSL.TLSTrustAll.socketFactory, Certs.TrustAll)
                            .hostnameVerifier { _, _ -> true }
                            .connectTimeout(payload.timeout, TimeUnit.MILLISECONDS)
                            .callTimeout(payload.timeout, TimeUnit.MILLISECONDS)
                            .readTimeout(payload.timeout, TimeUnit.MILLISECONDS)
                            .writeTimeout(payload.timeout, TimeUnit.MILLISECONDS)
                            .build()
                    )
                )
            )
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(msf)
            .setRenderersFactory(rf)
            .setTrackSelector(trackSelector)
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

    override fun play(url: String) {
        player.update { prev ->
            if (prev != null) {
                stop()
            }
            _url.update { url }
            createPlayer(payload.value).also {
                it.addListener(this)
                val mediaItem = MediaItem.fromUri(url)
                it.setMediaItem(mediaItem)
                it.prepare()
            }
        }
    }

    override fun stop() {
        _url.update { null }
        player.update {
            it?.removeListener(this)
            it?.stop()
            it?.release()
            null
        }
        super.mutablePlaybackState.value = Player.STATE_IDLE
        super.mutablePlaybackError.value = null
        super.mutableVideoSize.value = Rect()
    }

    override fun replay() {
        url.value?.let { play(it) }
    }

    override fun onVideoSizeChanged(size: VideoSize) {
        super.onVideoSizeChanged(size)
        super.mutableVideoSize.value = size.toRect()
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        super.mutablePlaybackState.value = state
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        super.onPlayerErrorChanged(error)
        if (pref.autoReconnect || error?.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.value?.let {
                it.seekToDefaultPosition()
                it.prepare()
            }
        }
        super.mutablePlaybackError.value = error
    }

    private fun VideoSize.toRect(): Rect {
        return Rect(0, 0, width, height)
    }
}
