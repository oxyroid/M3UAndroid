package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.R
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.XtreamInput
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.service.Messager
import com.m3u.i18n.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

@HiltWorker
class SubscriptionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val manager: NotificationManager,
    private val messager: Messager
) : CoroutineWorker(context, params) {
    private val dataSource = inputData
        .getString(INPUT_STRING_DATA_SOURCE_VALUE)
        ?.let { DataSource.ofOrNull(it) }

    private val title = inputData.getString(INPUT_STRING_TITLE)
    private val address = inputData.getString(INPUT_STRING_ADDRESS)
    private val username = inputData.getString(INPUT_STRING_USERNAME)
    private val password = inputData.getString(INPUT_STRING_PASSWORD)
    private val url = inputData.getString(INPUT_STRING_URL)

    override suspend fun doWork(): Result = coroutineScope {
        dataSource ?: return@coroutineScope Result.failure()
        createChannel()
        when (dataSource) {
            DataSource.M3U -> {
                title ?: return@coroutineScope Result.failure()
                url ?: return@coroutineScope Result.failure()
                if (title.isEmpty()) {
                    val message = context.getString(string.data_error_empty_title)
                    val data = workDataOf("message" to message)
                    Result.failure(data)
                } else {
                    try {
                        playlistRepository.m3u(title, url)
                        Result.success()
                    } catch (e: Exception) {
                        Result.failure()
                    }
                }
            }

            DataSource.Xtream -> {
                title ?: return@coroutineScope Result.failure()
                address ?: return@coroutineScope Result.failure()
                username ?: return@coroutineScope Result.failure()
                password ?: return@coroutineScope Result.failure()
                url ?: return@coroutineScope Result.failure()
                if (title.isEmpty()) {
                    val message = context.getString(string.data_error_empty_title)
                    val data = workDataOf("message" to message)
                    messager.emit(message)
                    Result.failure(data)
                } else {
                    try {
                        val type = XtreamInput.decodeFromUrl(url).type
                        playlistRepository.xtream(title, address, username, password, type)
                        Result.success()
                    } catch (e: Exception) {
                        messager.emit(e.message.orEmpty())
                        Result.failure(workDataOf("message" to e.message))
                    }
                }
            }

            else -> {
                val message = "unsupported data source $dataSource"
                messager.emit(message)
                Result.failure(workDataOf("message" to message))
            }
        }
    }

    private val builder = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.round_file_download_24)
        .setContentTitle(title.orEmpty())
        .setContentText(url.orEmpty())
        .setOngoing(true)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_NAME, NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "display subscribe task progress"
        manager.createNotificationChannel(channel)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            .setContentTitle(title)
            .setContentText(url)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_NAME = "subscribe task"
        private const val NOTIFICATION_ID = 1224
        const val INPUT_STRING_TITLE = "title"
        const val INPUT_STRING_URL = "url"
        const val INPUT_STRING_ADDRESS = "address"
        const val INPUT_STRING_USERNAME = "username"
        const val INPUT_STRING_PASSWORD = "password"
        const val INPUT_STRING_DATA_SOURCE_VALUE = "data-source"
        const val TAG = "subscription"
    }
}
