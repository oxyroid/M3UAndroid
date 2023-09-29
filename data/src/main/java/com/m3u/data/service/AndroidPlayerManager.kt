package com.m3u.data.service

import android.annotation.SuppressLint
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
import com.m3u.core.architecture.service.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import javax.inject.Inject

@OptIn(UnstableApi::class)
class AndroidPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    configuration: Configuration
) : PlayerManager(), Player.Listener, MediaSession.Callback {

    private val trustAllCert by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }
        }
    }

    private val sslContext by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAllCert), SecureRandom())
        }
    }

    private val okHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCert as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    override fun observe(): Flow<Player?> = playerFlow.asStateFlow()

    private val playerFlow = MutableStateFlow<Player?>(null)
    private val player: Player? get() = playerFlow.value

    private val isSSLVerification by configuration.isSSLVerification
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

    override fun installMedia(url: String) {
        player?.let {
            it.addListener(this)
            val mediaItem = MediaItem.fromUri(url)
            it.setMediaItem(mediaItem)
            it.prepare()
        }
    }

    override fun uninstallMedia() {
        player?.let {
            it.removeListener(this)
            it.stop()
        }
        super.playbackState.value = Player.STATE_IDLE
        super.playerError.value = null
        super.videoSize.value = Rect()
    }

    override fun onVideoSizeChanged(size: VideoSize) {
        super.onVideoSizeChanged(size)
        videoSize.value = Rect(0, 0, size.width, size.height)
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        playbackState.value = state
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
        super.playerError.value = error
    }
}