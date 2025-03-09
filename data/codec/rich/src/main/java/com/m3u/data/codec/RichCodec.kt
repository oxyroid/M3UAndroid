package com.m3u.data.codec

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import com.google.auto.service.AutoService
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

@AutoService(Codecs::class)
class RichCodec: Codecs {
    override fun createRenderersFactory(context: Context): RenderersFactory {
        return NextRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
    }
}