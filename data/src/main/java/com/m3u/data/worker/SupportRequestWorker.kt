package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SupportRequestWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return Result.success()
    }

    companion object {
        const val TAG = "support_request"

        fun start(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<SupportRequestWorker>()
                .addTag(TAG)
                .build()
            workManager.enqueue(request)
        }
    }
}
