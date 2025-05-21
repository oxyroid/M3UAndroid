package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.get
import com.m3u.data.BuildConfig
import com.m3u.data.api.OkhttpClient
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.service.Messager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@HiltWorker
class SupportRequestWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    private val workManager: WorkManager,
    private val settings: Settings,
    private val playlistRepository: PlaylistRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deviceId = try {
            settings[PreferencesKeys.DEVICE_ID]
        } catch (_: Exception) {
            return Result.failure()
        }

        val json = Json { ignoreUnknownKeys = true }
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/rest/v1/support_requests?device_id=eq.$deviceId&status=eq.pending")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .build()
        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.byteStream() ?: return Result.success().also { start(workManager) }
        val supportRequests: List<SupportRequest> = body.use { json.decodeFromStream(it) }
        val messager = EntryPointAccessors.fromApplication(context, MessagerEntryPoint::class.java).messager
        for (item in supportRequests) {
            if (item.actionType == ACTION_REFRESH_PROFILE) {
                val m3uUrl = item.payload?.m3uUrl ?: continue
                playlistRepository.refresh(m3uUrl)
                messager.emit("Profile refreshed")
            }
            patch(item.id)
        }
        start(workManager)
        return Result.success()
    }

    private fun patch(id: Int) {
        val body = "{\"status\":\"completed\"}".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/rest/v1/support_requests?id=eq.$id")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .method("PATCH", body)
            .build()
        okHttpClient.newCall(request).execute().close()
    }

    @Serializable
    private data class SupportRequest(
        val id: Int,
        @SerialName("action_type") val actionType: String,
        val payload: Payload? = null
    )

    @Serializable
    private data class Payload(
        @SerialName("m3u_url") val m3uUrl: String? = null
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MessagerEntryPoint {
        val messager: Messager
    }

    companion object {
        private const val TAG = "support_request"
        private const val ACTION_REFRESH_PROFILE = "refresh_profile"

        fun start(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<SupportRequestWorker>()
                .setInitialDelay(3, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
