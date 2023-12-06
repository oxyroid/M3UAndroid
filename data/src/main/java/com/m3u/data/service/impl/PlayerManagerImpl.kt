@file:kotlin.OptIn(ExperimentalConfiguration::class)
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.session.MediaSession
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.ExperimentalConfiguration
import com.m3u.core.architecture.configuration.observeAsFlow
import com.m3u.data.contract.Certs
import com.m3u.data.contract.SSL
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val configuration: Configuration
) : PlayerManager(), Player.Listener, MediaSession.Callback {
    private val player = MutableStateFlow<Player?>(null)
    private val currentPlayer: Player? get() = player.value

    override fun observe(): Flow<Player?> = player.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main) + Job()

    override fun initialize() {
        val isSSLVerification = configuration.observeAsFlow { it.isSSLVerification }
        val timeout = configuration.observeAsFlow { it.connectTimeout }

        combine(isSSLVerification, timeout) { i, t -> PlayerPayload(i, t) }
            .distinctUntilChanged()
            .onEach { payload ->
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
                player.update {
                    createPlayer(
                        factory = msf,
                        trackSelector = DefaultTrackSelector(context).apply {
                            setParameters(buildUponParameters().setMaxVideoSizeSd())
                        }
                    )
                }
            }
            .launchIn(scope)
    }

    private fun createPlayer(
        factory: MediaSource.Factory,
        trackSelector: TrackSelector
    ): Player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(factory)
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

    override fun install(url: String) {
        player.value?.let {
            it.addListener(this)
            val mediaItem = MediaItem.fromUri(url)
            it.setMediaItem(mediaItem)
            it.prepare()
        }
    }

    override fun uninstall() {
        currentPlayer?.let {
            it.removeListener(this)
            it.stop()
        }
        super.mutablePlaybackState.value = Player.STATE_IDLE
        super.mutablePlaybackError.value = null
        super.mutableVideoSize.value = Rect()
    }

    override fun destroy() {
        uninstall()
        currentPlayer?.release()
        player.update { null }
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
        when (error?.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                currentPlayer?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
            }

            else -> {}
        }
        super.mutablePlaybackError.value = error
    }

    private fun VideoSize.toRect(): Rect {
        return Rect(0, 0, width, height)
    }
}
