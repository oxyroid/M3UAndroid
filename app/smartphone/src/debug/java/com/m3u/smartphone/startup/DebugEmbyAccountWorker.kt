package com.m3u.smartphone.startup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.repository.provider.ProviderOperationException
import com.m3u.data.repository.provider.ProviderSubscriptionRequest
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
internal class DebugEmbyAccountWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val providerRepository: SubscriptionProviderRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val fixture = DebugEmbyAccountConfiguration.configuredOrNull()
            ?: return Result.success()
        if (!DebugDefaultLibraryWorker.hasSettled(applicationContext)) {
            return if (runAttemptCount < MAXIMUM_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        OUTPUT_FAILURE_CODE to
                            FAILURE_CODE_DEFAULT_LIBRARY_NOT_SETTLED
                    )
                )
            }
        }
        return try {
            val alreadyImported = providerRepository.observeAccountSummaries()
                .first()
                .any { account ->
                    account.providerId == BUILT_IN_PROVIDER_ID &&
                        account.providerKind == EmbyCompatibleProviderKinds.Emby &&
                        account.baseUrl.trimEnd('/') == fixture.baseUrl &&
                        account.username == fixture.username
                }
            if (!alreadyImported) {
                providerRepository.subscribe(
                    ProviderSubscriptionRequest(
                        title = fixture.title,
                        providerId = BUILT_IN_PROVIDER_ID,
                        providerKind = EmbyCompatibleProviderKinds.Emby,
                        settingValues = mapOf(
                            SubscriptionProviderSettingKeys.BaseUrl to fixture.baseUrl,
                            SubscriptionProviderSettingKeys.Username to fixture.username,
                        ),
                        credentialHandles = mapOf(
                            SubscriptionProviderSettingKeys.Password to
                                providerRepository.stageCredential(fixture.password),
                        ),
                    )
                )
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val providerFailure = error as? ProviderOperationException
            val failureCode = providerFailure?.code ?: error::class.java.simpleName
            Timber.tag(LOG_TAG).e(
                "Local debug Emby bootstrap failed (%s)",
                failureCode,
            )
            if (
                runAttemptCount < MAXIMUM_RETRY_COUNT &&
                (providerFailure == null || providerFailure.recoverable)
            ) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(OUTPUT_FAILURE_CODE to failureCode),
                )
            }
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "debug-emby-account-bootstrap"
        private const val LOG_TAG = "DebugEmbyBootstrap"
        private const val MAXIMUM_RETRY_COUNT = 2
        private const val FAILURE_CODE_DEFAULT_LIBRARY_NOT_SETTLED =
            "default-library-not-settled"
        internal const val OUTPUT_FAILURE_CODE = "debug_emby_failure_code"
        private val BUILT_IN_PROVIDER_ID =
            ExtensionId("com.m3u.provider.emby-compatible")

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<DebugEmbyAccountWorker>()
                .setInitialDelay(1, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
