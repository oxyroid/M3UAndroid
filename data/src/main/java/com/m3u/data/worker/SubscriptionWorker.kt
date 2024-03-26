package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.core.architecture.logger.Logger
import com.m3u.data.R
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.service.Messager
import com.m3u.i18n.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

@HiltWorker
class SubscriptionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val manager: NotificationManager,
    private val messager: Messager,
    private val logger: Logger
) : CoroutineWorker(context, params) {
    private val dataSource = inputData
        .getString(INPUT_STRING_DATA_SOURCE_VALUE)
        ?.let { DataSource.ofOrNull(it) }

    private val title = inputData.getString(INPUT_STRING_TITLE)
    private val basicUrl = inputData.getString(INPUT_STRING_BASIC_URL)
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
                        playlistRepository.m3u(
                            title = title,
                            url = url,
                            callback = { count, total ->
                                val notification = createNotification()
                                    .setContentText("[$count/${total.takeIf { it != -1 } ?: "~"}] Downloading...")
                                    .setProgress(total, count, count == -1)
                                    .build()
                                manager.notify(NOTIFICATION_ID, notification)
                            }
                        )
                        Result.success()
                    } catch (e: Exception) {
                        Result.failure()
                    } finally {
                        val notification = createNotification()
                            .setContentText("Completed")
                            .build()
                        manager.notify(NOTIFICATION_ID, notification)
                    }
                }
            }

            DataSource.Xtream -> {
                title ?: return@coroutineScope Result.failure()
                basicUrl ?: return@coroutineScope Result.failure()
                username ?: return@coroutineScope Result.failure()
                password ?: return@coroutineScope Result.failure()
                if (title.isEmpty()) {
                    url ?: return@coroutineScope Result.failure()
                    val message = context.getString(string.data_error_empty_title)
                    val data = workDataOf("message" to message)
                    messager.emit(message)
                    Result.failure(data)
                } else {
                    try {
                        val type = url?.let { XtreamInput.decodeFromPlaylistUrlOrNull(it)?.type }
                        playlistRepository.xtream(
                            title = title,
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            type = type
                        )
                        Result.success()
                    } catch (e: Exception) {
                        logger.log(e)
                        Result.failure(workDataOf("message" to e.message))
                    } finally {
                        val notification = createNotification()
                            .setContentText("Completed")
                            .build()
                        manager.notify(NOTIFICATION_ID, notification)
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

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_NAME, NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "display subscribe task progress"
        manager.createNotificationChannel(channel)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification().build())
    }

    private fun createNotification(): Notification.Builder {
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            .setContentTitle(title)
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_NAME = "subscribe task"
        private const val NOTIFICATION_ID = 1224
        const val INPUT_STRING_TITLE = "title"
        const val INPUT_STRING_URL = "url"
        const val INPUT_STRING_BASIC_URL = "basic_url"
        const val INPUT_STRING_USERNAME = "username"
        const val INPUT_STRING_PASSWORD = "password"
        const val INPUT_STRING_DATA_SOURCE_VALUE = "data-source"
        const val TAG = "subscription"

        fun m3u(
            workManager: WorkManager,
            title: String,
            url: String
        ) {
            workManager.cancelAllWorkByTag(url)
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_STRING_TITLE to title,
                        INPUT_STRING_URL to url,
                        INPUT_STRING_DATA_SOURCE_VALUE to DataSource.M3U.value
                    )
                )
                .addTag(url)
                .addTag(TAG)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueue(request)
        }

        fun xtream(
            workManager: WorkManager,
            title: String,
            url: String,
            basicUrl: String,
            username: String,
            password: String,
        ) {
            workManager.cancelAllWorkByTag(url)
            workManager.cancelAllWorkByTag(basicUrl)
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_STRING_TITLE to title,
                        INPUT_STRING_URL to url,
                        INPUT_STRING_BASIC_URL to basicUrl,
                        INPUT_STRING_USERNAME to username,
                        INPUT_STRING_PASSWORD to password,
                        INPUT_STRING_DATA_SOURCE_VALUE to DataSource.Xtream.value
                    )
                )
                .addTag(url)
                .addTag(basicUrl)
                .apply {
                    val xtreamInput = checkNotNull(XtreamInput.decodeFromPlaylistUrlOrNull(url))
                    if (xtreamInput.type == null) {
                        addTag(
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(
                                    type = DataSource.Xtream.TYPE_LIVE
                                )
                            )
                        )
                        addTag(
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(
                                    type = DataSource.Xtream.TYPE_SERIES
                                )
                            )
                        )
                        addTag(
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(
                                    type = DataSource.Xtream.TYPE_VOD
                                )
                            )
                        )
                    }
                }
                .addTag(TAG)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueue(request)
        }

        fun any(
            workManager: WorkManager,
            title: String,
            url: String,
            basicUrl: String? = null,
            username: String? = null,
            password: String? = null,
            dataSource: DataSource
        ) {
            when (dataSource) {
                DataSource.M3U -> {
                    m3u(workManager, title, url)
                }
                DataSource.Xtream -> {
                    val xtreamInput = XtreamInput.decodeFromPlaylistUrlOrNull(url)
                    when {
                        xtreamInput != null -> xtream(
                            workManager,
                            title,
                            url, // include type param
                            basicUrl ?: xtreamInput.basicUrl,
                            username ?: xtreamInput.username,
                            password ?: xtreamInput.password
                        )
                    }
                }
                else -> return
            }
        }
    }
}
