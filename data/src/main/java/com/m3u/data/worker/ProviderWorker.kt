package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.repository.provider.ProviderOperationException
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ProviderRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SubscriptionProviderRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString(INPUT_PLAYLIST_URL) ?: return Result.failure()
        return try {
            setProgress(workDataOf(OUTPUT_PROGRESS to 0))
            val refreshed = repository.refresh(
                playlistUrl,
                reason = SubscriptionRefreshReason.Background,
            )
            setProgress(workDataOf(OUTPUT_PROGRESS to 100))
            Result.success(workDataOf(OUTPUT_CHANNEL_COUNT to refreshed.channelCount))
        } catch (exception: ProviderOperationException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val INPUT_PLAYLIST_URL = "playlist-url"
        private const val OUTPUT_CHANNEL_COUNT = "channel-count"
        private const val OUTPUT_PROGRESS = "progress"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(workManager: WorkManager, playlistUrl: String) {
            val request = OneTimeWorkRequestBuilder<ProviderRefreshWorker>()
                .setInputData(workDataOf(INPUT_PLAYLIST_URL to playlistUrl))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(SubscriptionWorker.TAG)
                .addTag(playlistUrl)
                .build()
            workManager.enqueueUniqueWork(
                "provider-refresh:$playlistUrl",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

@HiltWorker
class ProviderSessionCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SubscriptionProviderRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        repository.closeOrphanedPlaybackSessions()
        Result.success()
    } catch (exception: ProviderOperationException) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ProviderSessionCleanupWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                "provider-session-cleanup",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

@HiltWorker
class ExtensionPluginBootstrapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ExtensionPluginRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { repository.restoreEnabled() }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                "extension-plugin-bootstrap",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ExtensionPluginBootstrapWorker>().build(),
            )
        }
    }
}
