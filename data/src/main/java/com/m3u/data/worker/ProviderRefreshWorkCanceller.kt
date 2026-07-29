package com.m3u.data.worker

import androidx.work.WorkManager
import androidx.work.await
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.DataSource
import com.m3u.extension.api.ExtensionId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ProviderRefreshWorkCanceller internal constructor(
    private val providerPlaylistUrls: suspend (String) -> List<String>,
    private val cancelUniqueWork: suspend (String) -> Unit,
) {
    @Inject
    constructor(
        providerDao: ProviderDao,
        workManager: WorkManager,
    ) : this(
        providerPlaylistUrls = { extensionId ->
            providerDao.getAccountsByProviderId(extensionId)
                .map { account -> account.playlistUrl }
        },
        cancelUniqueWork = { workName ->
            workManager.cancelUniqueWork(workName).await()
        },
    )

    suspend fun cancel(extensionId: ExtensionId) {
        providerPlaylistUrls(extensionId.value)
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .forEach { playlistUrl ->
                cancelUniqueWork(providerRefreshWorkName(playlistUrl))
            }
    }
}

internal fun providerRefreshWorkName(playlistUrl: String): String =
    playlistRefreshWorkTag(
        source = DataSource.Provider,
        url = playlistUrl,
    )
