package com.m3u.data.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.m3u.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository
) : CoroutineWorker(appContext, params) {

    private val uri = inputData.getString(INPUT_URI)?.let { Uri.parse(it) }

    override suspend fun doWork(): Result {
        uri?: return Result.failure()
        try {
            playlistRepository.restore(uri)
        } catch (e: Exception) {
            return Result.failure()
        }
        return Result.success()
    }

    companion object {
        const val TAG = "restore"
        const val INPUT_URI = "uri"
    }
}