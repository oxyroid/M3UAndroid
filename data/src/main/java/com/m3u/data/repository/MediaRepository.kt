package com.m3u.data.repository

import android.graphics.drawable.Drawable
import android.net.Uri
import com.m3u.core.wrapper.Resource
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface MediaRepository {
    fun savePicture(url: String): Flow<Resource<File>>
    fun openOutputStream(uri: Uri): OutputStream?
    fun openInputStream(uri: Uri): InputStream?

    suspend fun loadDrawable(url: String): Drawable?
    suspend fun installApk(channel: ByteReadChannel)
}
