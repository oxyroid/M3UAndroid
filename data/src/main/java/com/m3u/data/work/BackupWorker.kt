package com.m3u.data.work

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.m3u.data.R
import com.m3u.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository
) : CoroutineWorker(context, params) {
    private val uri = inputData.getString(INPUT_URI)?.let { Uri.parse(it) }
    override suspend fun doWork(): Result {
        uri ?: return Result.failure()
        try {
            playlistRepository.backupOrThrow(uri)
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
            .setContentText("Please wait")
            .build()
    }


    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_ID = 1225
        const val TAG = "backup"
        const val INPUT_URI = "uri"
    }
}