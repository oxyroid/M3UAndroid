package com.m3u.data.codec

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.exoplayer.RenderersFactory
import java.util.ServiceLoader

interface Codecs {
    fun createRenderersFactory(context: Context): RenderersFactory
    fun getThumbnail(context: Context, uri: Uri): Bitmap?

    companion object {
        fun load(): Codecs {
            return ServiceLoader.load(Codecs::class.java).first()
        }
    }
}