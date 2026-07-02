package com.m3u.data.repository.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MimeTypes
import coil.Coil
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

private const val BITMAP_QUALITY = 100

internal class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaRepository {
    private val timber = Timber.tag("MediaRepositoryImpl")
    private val applicationName = "M3U"
    private val pictureDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        applicationName
    )
    private val downloadDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        applicationName
    )

    override suspend fun savePicture(url: String): String = withContext(Dispatchers.IO) {
        val drawable = checkNotNull(loadDrawable(url))
        val bitmap = drawable.toBitmap()
        val name = "Picture_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return@withContext savePictureToMediaStore(bitmap, name)
        }

        pictureDirectory.mkdirs()
        val file = File(pictureDirectory, name)
        file.outputStream().buffered().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, BITMAP_QUALITY, it)
            it.flush()
        }
        file.absolutePath
    }

    private fun savePictureToMediaStore(bitmap: Bitmap, name: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$applicationName"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        )
        try {
            resolver.openOutputStream(uri)?.buffered()?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, BITMAP_QUALITY, it)
                it.flush()
            } ?: error("Cannot open output stream for $uri")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    override suspend fun installApk(channel: ByteReadChannel) = withContext(Dispatchers.IO) {
        val dir = downloadDirectory.resolve("apks")
        dir.mkdirs()
        val file = File(dir, "${System.currentTimeMillis()}.apk")
        channel.copyAndClose(file.writeChannel())
        val uri = Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            MimeTypes.APPLICATION_AIT
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)
    }

    override suspend fun loadDrawable(url: String): Drawable? = withContext(Dispatchers.IO) {
        val loader = Coil.imageLoader(context)
        val request: ImageRequest = ImageRequest.Builder(context)
            .data(url)
            .build()
        when (val result = loader.execute(request)) {
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
