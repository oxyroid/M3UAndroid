package com.m3u.data.service

import android.app.Notification
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import com.m3u.data.R
import com.m3u.i18n.R.string
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class StreamDownloadService : DownloadService(
    1,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    string.data_channel_name_stream_download_service,
    string.data_channel_description_stream_download_service,
) {
    @Inject
    lateinit var databaseProvider: StandaloneDatabaseProvider

    @Inject
    @get:JvmName("injectedDownloadManager")
    lateinit var downloadManager: DownloadManager

    override fun getDownloadManager(): DownloadManager = downloadManager

    override fun getScheduler(): Scheduler =
        WorkManagerScheduler(application, "stream-download-service-scheduler")

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification = DownloadNotificationHelper(
        application,
        DOWNLOAD_NOTIFICATION_CHANNEL_ID
    )
        .buildProgressNotification(
            application,
            R.drawable.round_file_download_24,
            null,
            null,
            downloads,
            notMetRequirements
        )

    companion object {
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    }
}