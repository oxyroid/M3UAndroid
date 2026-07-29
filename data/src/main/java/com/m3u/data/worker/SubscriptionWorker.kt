package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.R
import com.m3u.data.database.model.DataSource
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.playlist.PlaylistDataMaintenanceCoordinator
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.i18n.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID

@HiltWorker
class SubscriptionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val programmeRepository: ProgrammeRepository,
    private val notificationManager: NotificationManager,
    private val workManager: WorkManager,
) : CoroutineWorker(context, params) {
    private val dataSource = inputData
        .getString(INPUT_STRING_DATA_SOURCE_VALUE)
        ?.let { DataSource.ofOrNull(it) }

    private val title = inputData.getString(INPUT_STRING_TITLE)
    private val basicUrl = inputData.getString(INPUT_STRING_BASIC_URL)
    private val username = inputData.getString(INPUT_STRING_USERNAME)
    private val password = inputData.getString(INPUT_STRING_PASSWORD)
    private val url = inputData.getString(INPUT_STRING_URL)
    private val epgPlaylistUrl = inputData.getString(INPUT_STRING_EPG_PLAYLIST_URL)
    private val epgIgnoreCache = inputData.getBoolean(INPUT_BOOLEAN_EPG_IGNORE_CACHE, false)
    private val requireExistingPlaylist =
        inputData.getBoolean(INPUT_BOOLEAN_REQUIRE_EXISTING_PLAYLIST, false)
    private val notificationId: Int by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ATOMIC_NOTIFICATION_ID.incrementAndGet()
    }

    override suspend fun doWork(): Result = coroutineScope {
        dataSource ?: return@coroutineScope Result.failure()
        createChannel()
        coroutineContext[Job]?.invokeOnCompletion { cause ->
            when (cause) {
                null -> {}
                is CancellationException -> {
                    notificationManager.cancel(notificationId)
                }

                else -> {
                    createN10nBuilder()
                        .setContentText(context.getString(string.ui_error_unknown))
                        .setActions(retryAction)
                        .setColor(Color.RED)
                        .buildThenNotify()
                }
            }
        }
        when (dataSource) {
            DataSource.M3U -> {
                val url = url ?: return@coroutineScope Result.failure()
                PlaylistDataMaintenanceCoordinator.withExclusive {
                    val existing = if (requireExistingPlaylist) {
                        playlistRepository.get(url)
                    } else {
                        null
                    }
                    if (requireExistingPlaylist && existing == null) {
                        return@withExclusive Result.success()
                    }
                    try {
                        val title = existing?.title ?: title
                        val result = if (title == null) {
                            Result.failure()
                        } else if (title.isBlank()) {
                            val message = context.getString(string.data_error_empty_title)
                            createN10nBuilder()
                                .setContentText(message)
                                .buildThenNotify()
                            Result.failure()
                        } else {
                            var total = 0
                            playlistRepository.m3uOrThrow(title, url) { count ->
                                total = count
                                val notification = createN10nBuilder()
                                    .setContentText(findChannelProgressContentText(count))
                                    .setActions(cancelAction)
                                    .setOngoing(true)
                                    .build()
                                notificationManager.notify(notificationId, notification)
                            }

                            createN10nBuilder()
                                .setContentText(findCompleteContentText(total))
                                .buildThenNotify()
                            Result.success()
                        }
                        result
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        throw error
                    }
                }
            }

            DataSource.EPG -> {
                val playlistUrl = epgPlaylistUrl ?: return@coroutineScope Result.failure()
                val ignoreCache = epgIgnoreCache
                try {
                    programmeRepository.checkOrRefreshProgrammesOrThrow(
                        playlistUrl,
                        ignoreCache = ignoreCache
                    )
                        .onEach { count ->
                            val notification = createN10nBuilder()
                                .setContentText(findProgrammeProgressContentText(count))
                                .setActions(cancelAction)
                                .build()
                            notificationManager.notify(notificationId, notification)
                        }
                        .collect()
                    Result.success()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    createN10nBuilder()
                        .setContentText(context.getString(string.ui_error_unknown))
                        .setActions(retryAction)
                        .setColor(Color.RED)
                        .buildThenNotify()
                    Result.failure()
                }
            }

            DataSource.Xtream -> {
                title ?: return@coroutineScope Result.failure()
                basicUrl ?: return@coroutineScope Result.failure()
                username ?: return@coroutineScope Result.failure()
                password ?: return@coroutineScope Result.failure()
                PlaylistDataMaintenanceCoordinator.withExclusive {
                    val existing = if (requireExistingPlaylist && url != null) {
                        playlistRepository.get(url)
                    } else {
                        null
                    }
                    if (requireExistingPlaylist && existing == null) {
                        return@withExclusive Result.success()
                    }
                    val effectiveTitle = existing?.title ?: title
                    if (
                        effectiveTitle.isBlank() ||
                        basicUrl.isBlank() ||
                        username.isBlank() ||
                        password.isBlank()
                    ) {
                        url ?: return@withExclusive Result.failure()
                        val message = context.getString(string.data_error_empty_title)
                        createN10nBuilder()
                            .setContentText(message)
                            .buildThenNotify()
                        Result.failure()
                    } else {
                        try {
                        val type = url?.let { XtreamInput.decodeFromPlaylistUrlOrNull(it)?.type }
                        var total = 0
                        playlistRepository.xtreamOrThrow(
                            effectiveTitle, basicUrl, username, password, type
                        ) { count ->
                            total = count
                            val notification = createN10nBuilder()
                                .setContentText(findChannelProgressContentText(count))
                                .setActions(cancelAction)
                                .build()
                            notificationManager.notify(notificationId, notification)
                        }
                        createN10nBuilder()
                            .setContentText(findCompleteContentText(total))
                            .buildThenNotify()
                        Result.success()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            createN10nBuilder()
                                .setContentText(context.getString(string.ui_error_unknown))
                                .setActions(retryAction)
                                .setColor(Color.RED)
                                .buildThenNotify()
                            Result.failure()
                        }
                    }
                }
            }

            else -> {
                // do nothing
                Result.failure()
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(string.data_worker_subscription_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description =
            context.getString(string.data_worker_subscription_notification_channel_description)
        notificationManager.createNotificationChannel(channel)
    }

    private fun Notification.Builder.buildThenNotify() {
        if (isStopped) return
        notificationManager.notify(notificationId, build())
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(notificationId, createN10nBuilder().build())
    }

    private fun createN10nBuilder(): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            .setContentTitle(
                when (dataSource) {
                    DataSource.EPG ->
                        context.getString(string.data_worker_subscription_notification_channel_name)
                    else -> title
                }
            )

    private fun findCancelActionTitle() =
        context.getString(string.data_worker_subscription_action_cancel)

    private fun findRetryActionTitle() =
        context.getString(string.data_worker_subscription_action_retry)

    private fun findCompleteContentText(total: Int) =
        context.getString(string.data_worker_subscription_content_completed, total)

    private fun findChannelProgressContentText(count: Int) =
        context.getString(string.data_worker_subscription_content_channel_progress, count)

    private fun findProgrammeProgressContentText(count: Int) =
        context.getString(string.data_worker_subscription_content_programme_progress, count)

    private val cancelAction: Notification.Action by lazy {
        Notification.Action.Builder(
            Icon.createWithResource(
                context,
                R.drawable.round_cancel_24
            ),
            findCancelActionTitle(),
            workManager.createCancelPendingIntent(id)
        )
            .build()
    }
    private val retryAction: Notification.Action by lazy {
        Notification.Action.Builder(
            Icon.createWithResource(
                context,
                R.drawable.round_refresh_24
            ),
            findRetryActionTitle(),
            PendingIntent.getForegroundService(
                context,
                1234,
                Intent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val INPUT_STRING_TITLE = "title"
        private const val INPUT_STRING_URL = "url"
        private const val INPUT_STRING_EPG_PLAYLIST_URL = "epg"
        private const val INPUT_BOOLEAN_EPG_IGNORE_CACHE = "ignore_cache"
        private const val INPUT_BOOLEAN_REQUIRE_EXISTING_PLAYLIST =
            "require-existing-playlist"
        private const val INPUT_STRING_BASIC_URL = "basic_url"
        private const val INPUT_STRING_USERNAME = "username"
        private const val INPUT_STRING_PASSWORD = "password"
        private const val INPUT_STRING_DATA_SOURCE_VALUE = "data-source"
        const val TAG = "subscription"

        fun m3u(
            workManager: WorkManager,
            title: String,
            url: String,
            requireExistingPlaylist: Boolean = false,
        ): UUID {
            val uri = Uri.parse(url)
            val localSource = url.isLocalM3uSource()
            val workTag = playlistRefreshWorkTag(DataSource.M3U, url)
            val permissionTag = persistedUriPermissionTag(uri)
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_STRING_TITLE to title,
                        INPUT_STRING_URL to url,
                        INPUT_BOOLEAN_REQUIRE_EXISTING_PLAYLIST to requireExistingPlaylist,
                        INPUT_STRING_DATA_SOURCE_VALUE to DataSource.M3U.value
                    )
                )
                .addTag(workTag)
                .addTag(playlistWorkTag(url))
                .addTag(TAG)
                .addTag(DataSource.M3U.value)
                .apply {
                    if (localSource) {
                        addTag(permissionTag)
                    }
                }
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .apply {
                    if (!localSource) {
                        setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                    }
                }
                .build()
            if (localSource) {
                enqueuePersistedUriWork(
                    workManager = workManager,
                    permissionTag = permissionTag,
                ) {
                    workManager.enqueueUniqueWork(
                        workTag,
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                }
            } else {
                workManager.enqueueUniqueWork(
                    workTag,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
            return request.id
        }

        fun epg(
            workManager: WorkManager,
            playlistUrl: String,
            ignoreCache: Boolean
        ): UUID {
            val workTag = playlistRefreshWorkTag(DataSource.EPG, playlistUrl)
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_STRING_EPG_PLAYLIST_URL to playlistUrl,
                        INPUT_BOOLEAN_EPG_IGNORE_CACHE to ignoreCache,
                        INPUT_STRING_DATA_SOURCE_VALUE to DataSource.EPG.value,
                    )
                )
                .addTag(workTag)
                .addTag(playlistWorkTag(playlistUrl))
                .addTag(TAG)
                .addTag(DataSource.EPG.value)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                workTag,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            return request.id
        }

        fun xtream(
            workManager: WorkManager,
            title: String,
            url: String,
            basicUrl: String,
            username: String,
            password: String,
            requireExistingPlaylist: Boolean = false,
        ): UUID {
            val workTag = hashedWorkTag(
                namespace = "subscription-xtream",
                value = "$basicUrl\u0000$username",
            )
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_STRING_TITLE to title,
                        INPUT_STRING_URL to url,
                        INPUT_STRING_BASIC_URL to basicUrl,
                        INPUT_STRING_USERNAME to username,
                        INPUT_STRING_PASSWORD to password,
                        INPUT_BOOLEAN_REQUIRE_EXISTING_PLAYLIST to requireExistingPlaylist,
                        INPUT_STRING_DATA_SOURCE_VALUE to DataSource.Xtream.value
                    )
                )
                .addTag(workTag)
                .addTag(DataSource.Xtream.value)
                .apply {
                    if (url.isNotBlank()) {
                        addTag(xtreamPlaylistWorkTag(url))
                        addTag(playlistWorkTag(url))
                    }
                    val xtreamInput = XtreamInput.decodeFromPlaylistUrlOrNull(url) ?: XtreamInput(
                        basicUrl = basicUrl,
                        username = username,
                        password = password
                    )
                    val type = xtreamInput.type
                    val playlistUrls = if (type == null) {
                        listOf(
                            DataSource.Xtream.TYPE_LIVE,
                            DataSource.Xtream.TYPE_SERIES,
                            DataSource.Xtream.TYPE_VOD,
                        ).map { playlistType ->
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(type = playlistType)
                            )
                        }
                    } else {
                        listOf(
                            XtreamInput.encodeToPlaylistUrl(
                                xtreamInput.copy(type = type)
                            )
                        )
                    }
                    playlistUrls.forEach { playlistUrl ->
                        addTag(xtreamPlaylistWorkTag(playlistUrl))
                        addTag(playlistWorkTag(playlistUrl))
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
            workManager.enqueueUniqueWork(
                workTag,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            return request.id
        }

        private val ATOMIC_NOTIFICATION_ID = AtomicInteger()
    }
}

internal fun String.isLocalM3uSource(): Boolean =
    startsWith("content:", ignoreCase = true) ||
        startsWith("file:", ignoreCase = true)

internal fun m3uSubscriptionWorkName(url: String): String =
    playlistRefreshWorkTag(DataSource.M3U, url)

internal fun epgSubscriptionWorkName(url: String): String =
    playlistRefreshWorkTag(DataSource.EPG, url)

internal fun xtreamPlaylistWorkTag(url: String): String =
    playlistRefreshWorkTag(DataSource.Xtream, url)

fun playlistRefreshWorkTag(
    source: DataSource,
    url: String,
): String = when (source) {
    DataSource.M3U -> hashedWorkTag(namespace = "subscription-m3u", value = url)
    DataSource.EPG -> hashedWorkTag(namespace = "subscription-epg", value = url)
    DataSource.Xtream ->
        hashedWorkTag(namespace = "subscription-xtream-playlist", value = url)
    DataSource.Emby,
    DataSource.Jellyfin,
    DataSource.Provider ->
        hashedWorkTag(namespace = "provider-refresh", value = url)
    else -> hashedWorkTag(
        namespace = "subscription-${source.value}",
        value = url,
    )
}

fun playlistWorkTag(url: String): String =
    hashedWorkTag(namespace = "playlist-work", value = url)
