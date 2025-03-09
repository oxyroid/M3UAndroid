package com.m3u.data.codec

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import com.google.auto.service.AutoService

@AutoService(Codecs::class)
class LiteCodecs: Codecs {
    override fun createRenderersFactory(context: Context): RenderersFactory {
        return DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }
    }
}