package com.m3u.data.service.impl

import android.content.Context
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.ExperimentalConfiguration
import com.m3u.data.contract.Certs
import com.m3u.data.contract.SSL
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import javax.inject.Inject

@OptIn(UnstableApi::class)
class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    configuration: Configuration
) : PlayerManager(), Player.Listener, MediaSession.Callback {
    private val playerFlow = MutableStateFlow<Player?>(null)
    private val player: Player? get() = playerFlow.value

    @kotlin.OptIn(ExperimentalConfiguration::class)
    private val isSSLVerification by configuration.isSSLVerification

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(SSL.TLSTrustAll.socketFactory, Certs.TrustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    override fun observe(): Flow<Player?> = playerFlow.asStateFlow()

    override fun initialize() {
        playerFlow.value = ExoPlayer.Builder(context)
            .let {
                if (isSSLVerification) it
                else it.setMediaSourceFactory(
                    DefaultMediaSourceFactory(context).setDataSourceFactory(
                        DefaultDataSource.Factory(
                            context,
                            OkHttpDataSource.Factory(okHttpClient)
                        )
                    )
                )
            }
            .setTrackSelector(
                DefaultTrackSelector(context).apply {
                    setParameters(buildUponParameters().setMaxVideoSizeSd())
                }
            )
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

    override fun install(url: String) {
        player?.let {
            it.addListener(this)
            val mediaItem = MediaItem.fromUri(url)
            it.setMediaItem(mediaItem)
            it.prepare()
        }
    }

    override fun uninstall() {
        player?.let {
            it.removeListener(this)
            it.stop()
        }
        super.mutablePlaybackState.value = Player.STATE_IDLE
        super.mutablePlaybackError.value = null
        super.mutableVideoSize.value = Rect()
    }

    override fun destroy() {
        uninstall()
        player?.release()
        playerFlow.update { null }
    }

    override fun onVideoSizeChanged(size: VideoSize) {
        super.onVideoSizeChanged(size)
        super.mutableVideoSize.value = Rect(0, 0, size.width, size.height)
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        super.mutablePlaybackState.value = state
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        super.onPlayerErrorChanged(error)
        when (error?.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
            }

            else -> {}
        }
        super.mutablePlaybackError.value = error
    }
}
