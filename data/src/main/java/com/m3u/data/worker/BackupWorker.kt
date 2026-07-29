package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.m3u.data.R
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.i18n.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val notificationManager: NotificationManager,
) : CoroutineWorker(context, params) {
    private val uri = inputData.getString(INPUT_URI)?.toUri()
    override suspend fun doWork(): Result {
        createChannel()
        uri ?: return Result.failure()
        try {
            playlistRepository.backupOrThrow(uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.failure()
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            .setContentTitle(context.getString(string.data_worker_backup_notification_title))
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(string.data_worker_backup_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description =
            context.getString(string.data_worker_backup_notification_channel_description)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_ID = 1225
        const val TAG = "backup"
        const val UNIQUE_WORK_NAME = "playlist-backup"
        const val INPUT_URI = "uri"
    }
}
