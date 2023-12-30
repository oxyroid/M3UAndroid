package com.m3u.data.repository.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitException
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

private const val BITMAP_QUALITY = 100

class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) : MediaRepository {
    private val directory =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    override fun savePicture(url: String): Flow<Resource<File>> = resourceFlow {
        try {
            val drawable = loadDrawable(url)
            val bitmap = drawable.toBitmap()
            val name = "Picture_${System.currentTimeMillis()}.png"
            val file = File(directory, name)
            withContext(Dispatchers.IO) {
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                file.createNewFile()
                file.outputStream().buffered().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, BITMAP_QUALITY, it)
                    it.flush()
                }
            }
            emitResource(file)
        } catch (e: Exception) {
            logger.log(e)
            emitException(e)
        }
    }

    override suspend fun loadDrawable(url: String): Drawable {
        val loader = Coil.imageLoader(context)
        val request: ImageRequest = ImageRequest.Builder(context)
            .data(url)
            .build()
        return when (val result = loader.execute(request)) {
            is SuccessResult -> result.drawable
            is ErrorResult -> throw result.throwable
        }
    }

    override fun openOutputStream(uri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(uri)
    }

    override fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }
}
