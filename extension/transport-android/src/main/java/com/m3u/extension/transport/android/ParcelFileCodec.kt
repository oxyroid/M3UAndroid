package com.m3u.extension.transport.android

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File

object ParcelFileCodec {
    fun write(context: Context, content: String): ParcelFileDescriptor {
        val file = File.createTempFile("extension-", ".json", context.cacheDir)
        file.writeText(content)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        file.delete()
        return descriptor
    }

    fun read(descriptor: ParcelFileDescriptor, maximumBytes: Int): String = descriptor.use { current ->
        ParcelFileDescriptor.AutoCloseInputStream(current).use { input ->
            val bytes = input.readNBytes(maximumBytes + 1)
            require(bytes.size <= maximumBytes) { "Extension payload exceeds transport limit" }
            bytes.decodeToString()
        }
    }
}
