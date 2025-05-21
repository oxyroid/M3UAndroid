package com.m3u.tv.support

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.m3u.tv.BuildConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SupportRequestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        // TODO: Implement request using the constants above
        return Result.success()
    }

    companion object {
        const val TAG = "support_request"
    }
}
