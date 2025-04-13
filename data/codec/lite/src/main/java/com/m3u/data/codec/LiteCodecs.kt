package com.m3u.data.codec

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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

    override fun getThumbnail(context: Context, uri: Uri): Bitmap? = null
}