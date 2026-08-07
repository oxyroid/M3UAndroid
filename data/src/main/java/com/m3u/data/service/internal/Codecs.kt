package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import android.os.Handler
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
                ): AudioSink = DefaultAudioSink.Builder()
                    // Deliberately built WITHOUT a Context. DefaultAudioSink only
                    // honours the capabilities set here when no Context is given —
                    // otherwise it probes the actual output and silently discards
                    // them, which is exactly what happened on the first attempt.
                    .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()

                /**
                 * Route audio decoding to the bundled FFmpeg renderers, and only
                 * audio.
                 *
                 * Hardware AC3 decoders are built to feed a passthrough path;
                 * asked for PCM instead, some produce no output at all — the
                 * stream plays, the mixer runs, and nothing is audible.
                 *
                 * The mode is overridden here rather than on the factory because
                 * setExtensionRendererMode applies to every renderer: raising it
                 * globally also hands video to the software decoders, which on a
                 * modest TV box turns smooth playback into a slideshow. Measured,
                 * not assumed.
                 */
                override fun buildAudioRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    audioSink: AudioSink,
                    eventHandler: Handler,
                    eventListener: AudioRendererEventListener,
                    out: ArrayList<Renderer>
                ) {
                    super.buildAudioRenderers(
                        context,
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        audioSink,
                        eventHandler,
                        eventListener,
                        out
                    )
                }
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
