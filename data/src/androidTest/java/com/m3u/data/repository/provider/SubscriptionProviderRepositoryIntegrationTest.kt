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
import com.m3u.data.extension.security.AndroidKeystoreCredentialVault
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.CredentialResolver
import com.m3u.data.extension.security.ProviderBrokerScopeStore
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            val credentialVault = AndroidKeystoreCredentialVault(context)
            val principalRegistry = ActiveExtensionPrincipalRegistry()
            val provider = EmbyCompatibleProvider(
                client = client,
                credentialResolver = CredentialResolver(providerDao, credentialVault),
            )
            val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
            assertTrue(runtime.register(provider) is ExtensionRegistrationResult.Registered)
            val repository = SubscriptionProviderRepositoryImpl(
                context = context,
                runtime = runtime,
                providerDao = providerDao,
                playlistDao = playlistDao,
                importer = SubscriptionProviderImporter(
                    database = database,
                    playlistDao = playlistDao,
                    channelDao = channelDao,
                    providerDao = providerDao,
                    programmeDao = database.programmeDao(),
                    credentialVault = credentialVault,
                ),
                credentialVault = credentialVault,
                extensionContributionScheduler = NoOpExtensionContributionScheduler,
                extensionContributionRunCoordinator = ExtensionContributionRunCoordinator(),
                activePrincipalRegistry = principalRegistry,
                providerBrokerScopeStore = ProviderBrokerScopeStore(
                    credentialVault = credentialVault,
                    principalRegistry = principalRegistry,
                ),
                lifecycleCoordinator = ProviderLifecycleCoordinator(),
            )
            val serverUrl = InstrumentationRegistry.getArguments()
                .getString("m3uMockServerUrl")
                .orEmpty()
                .ifBlank { "http://10.0.2.2:8080" }

            val concurrentSubscriptions = List(2) {
                async(Dispatchers.Default) {
                    repository.subscribe(
                        ProviderSubscriptionRequest(
                            title = "Living Room Emby",
                            providerId = EmbyCompatibleProvider.ID,
                            providerKind = EmbyCompatibleProviderKinds.Emby,
                            settingValues = mapOf(
                                SubscriptionProviderSettingKeys.BaseUrl to serverUrl,
                                SubscriptionProviderSettingKeys.Username to "m3u",
                            ),
                            credentialHandles = mapOf(
                                SubscriptionProviderSettingKeys.Password to
                                    repository.stageCredential("m3u"),
                            ),
                        )
                    )
                }
            }.awaitAll()
            assertEquals(
                1,
                concurrentSubscriptions.map { result -> result.playlistUrl }.distinct().size,
            )
            assertEquals(1, providerDao.getAccounts().size)
            val subscription = concurrentSubscriptions.first()

            assertEquals(2, subscription.channelCount)
            assertEquals(DataSource.Provider, playlistDao.get(subscription.playlistUrl)?.source)
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
            val decryptedToken = requireNotNull(credentialVault.decrypt(credential))
            assertTrue(decryptedToken.isNotBlank())
            assertFalse(credential.ciphertext.contains(decryptedToken))
            assertTrue(channels.none { channel -> channel.url.contains(decryptedToken) })

            providerDao.invalidateCredential(account.id)
            assertTrue(
                repository.observeAccountSummaries().first().single().requiresReauthentication
            )
            val reauthenticated = repository.subscribe(
                ProviderSubscriptionRequest(
                    title = "Living Room Emby",
                    providerId = EmbyCompatibleProvider.ID,
                    providerKind = EmbyCompatibleProviderKinds.Emby,
                    settingValues = mapOf(
                        SubscriptionProviderSettingKeys.BaseUrl to serverUrl,
                        SubscriptionProviderSettingKeys.Username to "m3u",
                    ),
                    credentialHandles = mapOf(
                        SubscriptionProviderSettingKeys.Password to
                            repository.stageCredential("m3u"),
                    ),
                )
            )
            assertEquals(subscription.playlistUrl, reauthenticated.playlistUrl)
            assertFalse(
                repository.observeAccountSummaries().first().single().requiresReauthentication
            )

            val finalPlayback = requireNotNull(repository.resolvePlayback(news.id))
            val finalSession = requireNotNull(finalPlayback.session)
            repository.removeAccount(subscription.playlistUrl)
            assertNull(providerDao.getAccountByPlaylistUrl(subscription.playlistUrl))
            assertTrue(providerDao.getPlaybackSessions().none { session -> session.id == finalSession.id })
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

    private data object NoOpExtensionContributionScheduler : ExtensionContributionScheduler {
        override suspend fun enqueue(playlistUrl: String) = Unit

        override suspend fun cancel(playlistUrl: String) = Unit
    }
}
