package com.m3u.data.repository.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.core.foundation.architecture.Abi
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.emby.EmbyCompatibleProvider
import com.m3u.data.extension.emby.OkHttpEmbyCompatibleClient
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionProviderRepositoryIntegrationTest {
    @Test
    fun subscribeRefreshAndPlaybackUseStablePersistedReferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val playlistDao = database.playlistDao()
            val channelDao = database.channelDao()
            val providerDao = database.providerDao()
            val client = OkHttpEmbyCompatibleClient(
                context = context,
                publisher = TestPublisher,
                okHttpClient = OkHttpClient(),
            )
            val provider = EmbyCompatibleProvider(client)
            val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
            assertTrue(runtime.register(provider) is ExtensionRegistrationResult.Registered)
            val repository = SubscriptionProviderRepositoryImpl(
                runtime = runtime,
                providerDao = providerDao,
                playlistDao = playlistDao,
                importer = SubscriptionProviderImporter(
                    database = database,
                    playlistDao = playlistDao,
                    channelDao = channelDao,
                    providerDao = providerDao,
                ),
            )
            val serverUrl = InstrumentationRegistry.getArguments()
                .getString("m3uMockServerUrl")
                .orEmpty()
                .ifBlank { "http://10.0.2.2:8080" }

            val subscription = repository.subscribe(
                ProviderSubscriptionRequest(
                    title = "Living Room Emby",
                    baseUrl = serverUrl,
                    username = "m3u",
                    password = "m3u",
                    source = DataSource.Emby,
                )
            )

            assertEquals(2, subscription.channelCount)
            assertEquals(DataSource.Emby, playlistDao.get(subscription.playlistUrl)?.source)
            val channels = channelDao.getByPlaylistUrl(subscription.playlistUrl)
            assertEquals(2, channels.size)
            assertTrue(channels.all { channel -> channel.url == Channel.URL_DYNAMIC })
            val news = channels.single { channel -> channel.relationId == "mock.news" }
            val reference = providerDao.getPlaybackReference(news.id)
            assertNotNull(reference)
            assertEquals("mock.news", reference?.itemId)

            val resolved = requireNotNull(repository.resolvePlayback(news.id))
            assertFalse(resolved.url == Channel.URL_DYNAMIC)
            assertTrue(resolved.headers.isNotEmpty())
            assertTrue(
                repository.closePlayback(
                    session = requireNotNull(resolved.session),
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
            )

            channelDao.favouriteOrUnfavourite(news.id, true)
            channelDao.updateSeen(news.id, 42L)
            repository.refresh(subscription.playlistUrl)
            val refreshed = requireNotNull(channelDao.get(news.id))
            assertTrue(refreshed.favourite)
            assertEquals(42L, refreshed.seen)

            val account = requireNotNull(providerDao.getAccountByPlaylistUrl(subscription.playlistUrl))
            val credential = requireNotNull(providerDao.getCredential(account.id))
            assertTrue(credential.accessToken.isNotBlank())
            assertTrue(channels.none { channel -> channel.url.contains(credential.accessToken) })

            val finalPlayback = requireNotNull(repository.resolvePlayback(news.id))
            repository.removeAccount(subscription.playlistUrl)
            assertTrue(
                repository.closePlayback(
                    session = requireNotNull(finalPlayback.session),
                    reason = ProviderPlaybackCloseReason.STOPPED,
                )
            )
        } finally {
            database.close()
        }
    }

    private data object TestPublisher : Publisher {
        override val applicationId = "com.m3u.data.test"
        override val versionName = "test"
        override val versionCode = 1
        override val debug = true
        override val model = "Android test"
        override val abi = Abi.x86_64
    }
}
