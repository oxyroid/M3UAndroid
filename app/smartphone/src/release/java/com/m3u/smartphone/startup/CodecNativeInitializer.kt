package com.m3u.smartphone.startup

import android.content.Context
import androidx.startup.Initializer
import com.m3u.data.codec.CodecNativeLoader

@Suppress("Unused")
class CodecNativeInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        CodecNativeLoader.initialize(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}