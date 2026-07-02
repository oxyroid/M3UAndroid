package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.m3u.data.R
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.i18n.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RestoreWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val notificationManager: NotificationManager,
) : CoroutineWorker(context, params) {
    private val uri = inputData.getString(INPUT_URI)?.let { Uri.parse(it) }

    override suspend fun doWork(): Result {
        createChannel()
        uri ?: return Result.failure()
        try {
            playlistRepository.restoreOrThrow(uri)
        } catch (e: Exception) {
            return Result.failure()
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            .setContentTitle(context.getString(string.data_worker_restore_content_title))
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(string.data_worker_restore_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = context.getString(string.data_worker_restore_channel_description)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "restore_channel"
        private const val NOTIFICATION_ID = 1226
        const val TAG = "restore"
        const val INPUT_URI = "uri"
    }
}
