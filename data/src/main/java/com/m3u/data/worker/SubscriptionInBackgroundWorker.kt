package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.wrapper.ProgressResource
import com.m3u.data.R
import com.m3u.data.repository.FeedRepository
import com.m3u.i18n.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope

@HiltWorker
class SubscriptionInBackgroundWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val feedRepository: FeedRepository,
    private val manager: NotificationManager,
) : CoroutineWorker(context, params) {
    private val title = inputData.getString(INPUT_STRING_TITLE)
    private val url = inputData.getString(INPUT_STRING_URL)
    private val strategy = inputData.getInt(INPUT_INT_STRATEGY, FeedStrategy.SKIP_FAVORITE)
    override suspend fun doWork(): Result = coroutineScope {
        title ?: return@coroutineScope Result.failure()
        url ?: return@coroutineScope Result.failure()
        createChannel()
        if (title.isEmpty()) {
            val message = context.getString(string.data_error_empty_title)
            val data = workDataOf("message" to message)
            failure(message)
            Result.failure(data)
        } else {
            try {
                collectFlow(title, url, strategy)
                Result.success()
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }

    private val builder = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.round_file_download_24)
        .setContentTitle(title.orEmpty())
        .setContentText(url.orEmpty())
        .setOngoing(true)

    private val id: Int = System.currentTimeMillis().toInt()

    private suspend fun collectFlow(
        title: String,
        url: String,
        strategy: Int
    ) = supervisorScope {
        feedRepository
            .subscribe(title, url, strategy)
            .collect { resource ->
                when (resource) {
                    is ProgressResource.Loading -> sendProgress(resource.value)
                    is ProgressResource.Success -> success()
                    is ProgressResource.Failure -> failure(resource.message)
                }
                if (resource !is ProgressResource.Loading) {
                    cancel()
                }
            }
    }

    private fun sendProgress(value: Int) {
        builder.setContentText("$value")
        manager.notify(id, builder.build())
    }

    private fun success() {
        builder
            .setContentText("completed")
            .setOngoing(false)
        manager.notify(id, builder.build())
    }

    private fun failure(message: String?) {
        builder
            .setContentText(message.orEmpty())
            .setOngoing(false)

        manager.notify(id, builder.build())
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, NOTIFICATION_NAME, NotificationManager.IMPORTANCE_LOW)
        channel.description = "display subscribe task progress"
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_NAME = "subscribe task"
        const val INPUT_STRING_TITLE = "title"
        const val INPUT_STRING_URL = "url"
        const val INPUT_INT_STRATEGY = "strategy"
    }
}