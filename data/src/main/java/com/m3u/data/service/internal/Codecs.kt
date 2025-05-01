package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder

object Codecs {
    fun createRenderersFactory(context: Context): RenderersFactory {
        return NextRenderersFactory(context).apply {
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