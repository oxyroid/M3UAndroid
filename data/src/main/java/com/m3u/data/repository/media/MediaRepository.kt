package com.m3u.data.repository.media

import android.graphics.drawable.Drawable
import android.net.Uri
import io.ktor.utils.io.ByteReadChannel
import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface MediaRepository {
    suspend fun savePicture(url: String): File
    fun openOutputStream(uri: Uri): OutputStream?
    fun openInputStream(uri: Uri): InputStream?

    suspend fun loadDrawable(url: String): Drawable?
    suspend fun installApk(channel: ByteReadChannel)
}
