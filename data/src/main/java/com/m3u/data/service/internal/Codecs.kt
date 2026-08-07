package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder

object Codecs {
    /**
     * @param disableAudioPassthrough decode compressed audio locally instead of
     * handing it to the sink untouched.
     *
     * ExoPlayer asks the audio output what it can handle, and when the sink
     * claims AC3 or EAC3 support it forwards the stream as-is. That is the right
     * call with an A/V receiver, but on devices where the box itself feeds the
     * speakers it has an unwanted side effect: the stream never reaches the
     * mixer, so the volume keys stop working on it. Users see a stereo channel
     * respond normally while a 5.1 movie ignores the remote entirely.
     *
     * Declaring stereo-only capabilities makes ExoPlayer decode instead — the
     * FFmpeg renderers below already handle AC3 and EAC3 — and audio flows back
     * through the mixer where volume applies.
     */
    @OptIn(UnstableApi::class)
    fun createRenderersFactory(
        context: Context,
        disableAudioPassthrough: Boolean = false
    ): RenderersFactory {
        val factory = if (disableAudioPassthrough) {
            object : NextRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink = DefaultAudioSink.Builder(context)
                    // Stereo PCM only: nothing is advertised as passthrough-capable,
                    // so every compressed format takes the decoding path.
                    .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        } else {
            NextRenderersFactory(context)
        }
        return factory.apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
    }

    fun getThumbnail(context: Context, uri: Uri): Bitmap? {
        val mediaInfo = MediaInfoBuilder()
            .from(context, uri)
            .build()
        val frame = mediaInfo?.getFrame()
        mediaInfo?.release()
        return frame
    }
}
