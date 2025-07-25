package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.m3u.data.R
import com.m3u.data.repository.playlist.PlaylistRepository
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
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            .setContentTitle("Backing up")
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_NAME, NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "display subscribe task progress"
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_NAME = "restore task"
        private const val NOTIFICATION_ID = 1226
        const val TAG = "restore"
        const val INPUT_URI = "uri"
    }
}