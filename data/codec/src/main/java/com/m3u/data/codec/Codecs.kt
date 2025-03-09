package com.m3u.data.codec

import android.content.Context
import androidx.media3.exoplayer.RenderersFactory
import java.util.ServiceLoader

interface Codecs {
    fun createRenderersFactory(context: Context): RenderersFactory

    companion object {
        fun load(): Codecs {
            return ServiceLoader.load(Codecs::class.java).first()
        }
    }
}