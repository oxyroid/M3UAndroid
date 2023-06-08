package com.m3u.data.repository.impl

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.m3u.core.architecture.logger.FileLoggerImpl
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.executeResult
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
import com.m3u.data.repository.MediaRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepositoryImpl constructor(
    private val context: Context,
    @FileLoggerImpl private val logger: Logger
) : MediaRepository {
    private val directory =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    override fun savePicture(url: String): Flow<Resource<File>> = resourceFlow {
        try {
            val drawable = loadDrawable(url)
            val bitmap = drawable.toBitmap()
            val name = "Picture_${System.currentTimeMillis()}.png"
            val file = File(directory, name)
            if (!file.exists()) {
                withContext(Dispatchers.IO) {
                    file.createNewFile()
                }
            }
            withContext(Dispatchers.IO) {
                file.outputStream().buffered().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    it.flush()
                }
            }
            emitResource(file)
        } catch (e: Exception) {
            logger.log(e)
            emitMessage(e.message)
        }
    }

    private suspend fun loadDrawable(url: String): Drawable {
        val loader = Coil.imageLoader(context)
        val request: ImageRequest = ImageRequest.Builder(context)
            .data(url)
            .build()
        return when (val result = loader.execute(request)) {
            is SuccessResult -> result.drawable
            is ErrorResult -> throw result.throwable
        }
    }

    override fun shareFiles(files: List<File>): Result<Unit> = logger.executeResult {
        val uris: List<Uri> = files.mapNotNull {
            try {
                FileProvider.getUriForFile(
                    context,
                    "com.m3u.app.fileprovider",
                    it
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        val intent = Intent(Intent.ACTION_MEDIA_SHARED, uris.first())
        context.startActivity(intent)
    }
}