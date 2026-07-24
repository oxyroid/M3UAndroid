package com.m3u.data.repository.playlist

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.parser.m3u.M3UParserImpl
import com.m3u.data.parser.xtream.XtreamParserImpl
import com.m3u.data.repository.BackupOrRestoreContracts
import com.m3u.data.repository.ProviderAccountBackup
import com.m3u.data.repository.ProviderPlaybackReferenceBackup
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.data.repository.provider.DiscoveredSubscriptionProvider
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.ProviderLifecycleCoordinator
import com.m3u.data.repository.provider.ProviderPlaybackCloseReason
import com.m3u.data.repository.provider.ProviderPlaybackSession
import com.m3u.data.repository.provider.ProviderPlaybackSource
import com.m3u.data.repository.provider.ProviderSessionCleanupResult
import com.m3u.data.repository.provider.ProviderSubscriptionRequest
import com.m3u.data.repository.provider.ProviderSubscriptionResult
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryProviderRestoreTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun ordinaryRestoreRemapsCollisionWithoutReplacingExistingProviderData() = runBlocking {
        withTestRepository { database, repository, context ->
            val account = providerAccount()
            val providerPlaylist = Playlist(
                title = "Existing provider",
                url = account.playlistUrl,
                source = DataSource.Provider,
            )
            val credential = ProviderCredentialEntity(
                accountId = account.id,
                credentialHandle = "provider-secret:existing",
                ciphertext = "ciphertext",
                nonce = "nonce",
                keyVersion = 1,
            )
            val providerChannel = Channel(
                id = COLLIDING_CHANNEL_ID,
                title = "Existing provider channel",
                category = "Live",
                playlistUrl = providerPlaylist.url,
                url = Channel.URL_DYNAMIC,
                relationId = REFERENCED_REMOTE_CHANNEL_ID,
            )
            val playbackReference = ChannelPlaybackReference(
                channelId = providerChannel.id,
                accountId = account.id,
                providerId = account.providerId,
                itemId = REFERENCED_REMOTE_CHANNEL_ID,
                mediaSourceId = "source",
                sourceType = "live",
            )
            database.playlistDao().insertOrReplace(providerPlaylist)
            database.providerDao().insertOrReplace(account)
            database.providerDao().insertOrReplace(credential)
            database.channelDao().insertOrReplace(providerChannel)
            database.providerDao().insertOrReplace(playbackReference)

            val ordinaryPlaylist = Playlist(
                title = "Ordinary backup",
                url = "https://backup.example/playlist.m3u",
            )
            val collidingOrdinaryChannel = Channel(
                id = providerChannel.id,
                title = "Colliding ordinary channel",
                category = "Live",
                playlistUrl = ordinaryPlaylist.url,
                url = "https://backup.example/colliding",
            )
            val nonCollidingOrdinaryChannel = Channel(
                id = ORDINARY_CHANNEL_ID,
                title = "Non-colliding ordinary channel",
                category = "Live",
                playlistUrl = ordinaryPlaylist.url,
                url = "https://backup.example/non-colliding",
            )
            val backup = createBackup(
                context = context,
                records = listOf(
                    BackupOrRestoreContracts.wrapPlaylist(
                        json.encodeToString(ordinaryPlaylist)
                    ),
                    BackupOrRestoreContracts.wrapChannel(
                        json.encodeToString(collidingOrdinaryChannel)
                    ),
                    BackupOrRestoreContracts.wrapChannel(
                        json.encodeToString(nonCollidingOrdinaryChannel)
                    ),
                ),
            )

            try {
                repository.restoreOrThrow(Uri.fromFile(backup))
            } finally {
                backup.delete()
            }

            assertEquals(providerPlaylist, database.playlistDao().get(providerPlaylist.url))
            assertEquals(account, database.providerDao().getAccount(account.id))
            assertEquals(credential, database.providerDao().getCredential(account.id))
            assertEquals(providerChannel, database.channelDao().get(providerChannel.id))
            assertEquals(
                playbackReference,
                database.providerDao().getPlaybackReference(providerChannel.id),
            )
            assertEquals(
                ordinaryPlaylist,
                database.playlistDao().get(ordinaryPlaylist.url),
            )

            val restoredOrdinaryChannels =
                database.channelDao().getByPlaylistUrl(ordinaryPlaylist.url)
            assertEquals(2, restoredOrdinaryChannels.size)
            val remappedOrdinaryChannel = restoredOrdinaryChannels.single { channel ->
                channel.url == collidingOrdinaryChannel.url
            }
            assertTrue(remappedOrdinaryChannel.id > 0)
            assertFalse(remappedOrdinaryChannel.id == providerChannel.id)
            assertEquals(
                collidingOrdinaryChannel.copy(id = remappedOrdinaryChannel.id),
                remappedOrdinaryChannel,
            )
            assertEquals(
                nonCollidingOrdinaryChannel,
                database.channelDao().get(nonCollidingOrdinaryChannel.id),
            )
        }
    }

    @Test
    fun providerRestoreAllocatesFreshChannelIdAndRemapsPlaybackReference() = runBlocking {
        withTestRepository { database, repository, context ->
            val targetPlaylist = Playlist(
                title = "Target",
                url = "https://target.example/playlist.m3u",
            )
            val existingChannel = Channel(
                id = COLLIDING_CHANNEL_ID,
                title = "Existing",
                category = "Target",
                playlistUrl = targetPlaylist.url,
                url = "https://target.example/live",
                favourite = true,
            )
            database.playlistDao().insertOrReplace(targetPlaylist)
            database.channelDao().insertOrReplace(existingChannel)

            val providerAccount = providerAccount()
            val providerPlaylist = Playlist(
                title = "Restored provider",
                url = providerAccount.playlistUrl,
                source = DataSource.Provider,
            )
            val providerChannels = List(PROVIDER_CHANNEL_BATCH_SIZE) { index ->
                Channel(
                    id = if (index == 0) {
                        COLLIDING_CHANNEL_ID
                    } else {
                        FIRST_NON_COLLIDING_PROVIDER_CHANNEL_ID + index
                    },
                    title = "Provider channel $index",
                    category = "Live",
                    playlistUrl = providerPlaylist.url,
                    url = Channel.URL_DYNAMIC,
                    relationId = if (index == 0) {
                        REFERENCED_REMOTE_CHANNEL_ID
                    } else {
                        "remote-channel-$index"
                    },
                )
            }
            val referencedProviderChannel = providerChannels.first()
            val ordinaryPlaylist = Playlist(
                title = "Ordinary backup",
                url = "https://backup.example/playlist.m3u",
            )
            val ordinaryChannel = Channel(
                id = ORDINARY_CHANNEL_ID,
                title = "Ordinary channel",
                category = "Live",
                playlistUrl = ordinaryPlaylist.url,
                url = "https://backup.example/live",
            )
            val reference = ProviderPlaybackReferenceBackup(
                channelId = referencedProviderChannel.id,
                accountId = providerAccount.id,
                providerId = providerAccount.providerId,
                itemId = REFERENCED_REMOTE_CHANNEL_ID,
                mediaSourceId = "source",
                sourceType = "live",
            )
            val backup = createBackup(
                context = context,
                records = buildList {
                    add(
                        BackupOrRestoreContracts.wrapPlaylist(
                            json.encodeToString(providerPlaylist)
                        )
                    )
                    providerChannels.forEach { channel ->
                        add(
                            BackupOrRestoreContracts.wrapChannel(
                                json.encodeToString(channel)
                            )
                        )
                    }
                    add(
                        BackupOrRestoreContracts.wrapPlaylist(
                            json.encodeToString(ordinaryPlaylist)
                        )
                    )
                    add(
                        BackupOrRestoreContracts.wrapChannel(
                            json.encodeToString(ordinaryChannel)
                        )
                    )
                    add(
                        BackupOrRestoreContracts.wrapProviderAccount(
                            json.encodeToString(
                                requireNotNull(ProviderAccountBackup.fromEntity(providerAccount))
                            )
                        )
                    )
                    add(
                        BackupOrRestoreContracts.wrapPlaybackReference(
                            json.encodeToString(reference)
                        )
                    )
                },
            )

            try {
                repository.restoreOrThrow(Uri.fromFile(backup))
            } finally {
                backup.delete()
            }

            assertEquals(existingChannel, database.channelDao().get(COLLIDING_CHANNEL_ID))
            val restoredProviderChannels = database.channelDao()
                .getByPlaylistUrl(providerPlaylist.url)
            assertEquals(PROVIDER_CHANNEL_BATCH_SIZE, restoredProviderChannels.size)
            val restoredProviderChannel = restoredProviderChannels.single { channel ->
                channel.relationId == REFERENCED_REMOTE_CHANNEL_ID
            }
            assertTrue(restoredProviderChannel.id > 0)
            assertFalse(restoredProviderChannel.id == COLLIDING_CHANNEL_ID)
            assertEquals(
                ordinaryChannel,
                database.channelDao().get(ORDINARY_CHANNEL_ID),
            )
            assertNull(
                database.providerDao().getPlaybackReference(COLLIDING_CHANNEL_ID)
            )
            val restoredReference = database.providerDao()
                .getPlaybackReference(restoredProviderChannel.id)
            assertNotNull(restoredReference)
            assertEquals(restoredProviderChannel.id, restoredReference?.channelId)
            assertEquals(reference.accountId, restoredReference?.accountId)
            assertEquals(reference.itemId, restoredReference?.itemId)
        }
    }

    @Test
    fun providerRestoreRejectsDuplicateBackupChannelIdsAtomically() = runBlocking {
        withTestRepository { database, repository, context ->
            val targetPlaylist = Playlist(
                title = "Target",
                url = "https://target.example/playlist.m3u",
            )
            val existingChannel = Channel(
                id = COLLIDING_CHANNEL_ID,
                title = "Existing",
                category = "Target",
                playlistUrl = targetPlaylist.url,
                url = "https://target.example/live",
            )
            database.playlistDao().insertOrReplace(targetPlaylist)
            database.channelDao().insertOrReplace(existingChannel)

            val providerAccount = providerAccount()
            val providerPlaylist = Playlist(
                title = "Restored provider",
                url = providerAccount.playlistUrl,
                source = DataSource.Provider,
            )
            val duplicateChannels = listOf("First", "Second").map { title ->
                Channel(
                    id = COLLIDING_CHANNEL_ID,
                    title = title,
                    category = "Live",
                    playlistUrl = providerPlaylist.url,
                    url = Channel.URL_DYNAMIC,
                    relationId = "remote-$title",
                )
            }
            val backup = createBackup(
                context = context,
                records = buildList {
                    add(
                        BackupOrRestoreContracts.wrapPlaylist(
                            json.encodeToString(providerPlaylist)
                        )
                    )
                    duplicateChannels.forEach { channel ->
                        add(
                            BackupOrRestoreContracts.wrapChannel(
                                json.encodeToString(channel)
                            )
                        )
                    }
                    add(
                        BackupOrRestoreContracts.wrapProviderAccount(
                            json.encodeToString(
                                requireNotNull(ProviderAccountBackup.fromEntity(providerAccount))
                            )
                        )
                    )
                },
            )

            val failure = try {
                repository.restoreOrThrow(Uri.fromFile(backup))
                null
            } catch (error: IllegalArgumentException) {
                error
            } finally {
                backup.delete()
            }

            assertNotNull(failure)
            assertEquals(existingChannel, database.channelDao().get(COLLIDING_CHANNEL_ID))
            assertTrue(database.channelDao().getByPlaylistUrl(providerPlaylist.url).isEmpty())
            assertNull(database.playlistDao().get(providerPlaylist.url))
            assertNull(database.providerDao().getAccount(providerAccount.id))
        }
    }

    @Test
    fun providerRestoreRejectsDuplicateChannelReferencesAtomically() = runBlocking {
        withTestRepository { database, repository, context ->
            val targetPlaylist = Playlist(
                title = "Target",
                url = "https://target.example/playlist.m3u",
            )
            val existingChannel = Channel(
                id = COLLIDING_CHANNEL_ID,
                title = "Existing",
                category = "Target",
                playlistUrl = targetPlaylist.url,
                url = "https://target.example/live",
            )
            database.playlistDao().insertOrReplace(targetPlaylist)
            database.channelDao().insertOrReplace(existingChannel)

            val providerAccount = providerAccount()
            val providerPlaylist = Playlist(
                title = "Restored provider",
                url = providerAccount.playlistUrl,
                source = DataSource.Provider,
            )
            val duplicateReferenceChannels = listOf("First", "Second").mapIndexed { index, title ->
                Channel(
                    id = COLLIDING_CHANNEL_ID + index + 1,
                    title = title,
                    category = "Live",
                    playlistUrl = providerPlaylist.url,
                    url = Channel.URL_DYNAMIC,
                    relationId = REFERENCED_REMOTE_CHANNEL_ID,
                )
            }
            val backup = createBackup(
                context = context,
                records = buildList {
                    add(
                        BackupOrRestoreContracts.wrapPlaylist(
                            json.encodeToString(providerPlaylist)
                        )
                    )
                    duplicateReferenceChannels.forEach { channel ->
                        add(
                            BackupOrRestoreContracts.wrapChannel(
                                json.encodeToString(channel)
                            )
                        )
                    }
                    add(
                        BackupOrRestoreContracts.wrapProviderAccount(
                            json.encodeToString(
                                requireNotNull(ProviderAccountBackup.fromEntity(providerAccount))
                            )
                        )
                    )
                },
            )

            val failure = try {
                repository.restoreOrThrow(Uri.fromFile(backup))
                null
            } catch (error: IllegalArgumentException) {
                error
            } finally {
                backup.delete()
            }

            assertNotNull(failure)
            assertEquals(existingChannel, database.channelDao().get(COLLIDING_CHANNEL_ID))
            assertTrue(database.channelDao().getByPlaylistUrl(providerPlaylist.url).isEmpty())
            assertNull(database.playlistDao().get(providerPlaylist.url))
            assertNull(database.providerDao().getAccount(providerAccount.id))
        }
    }

    @Test
    fun providerRestoreRejectsDuplicatePlaybackReferencesAtomically() = runBlocking {
        withTestRepository { database, repository, context ->
            val targetPlaylist = Playlist(
                title = "Target",
                url = "https://target.example/playlist.m3u",
            )
            val existingChannel = Channel(
                id = COLLIDING_CHANNEL_ID,
                title = "Existing",
                category = "Target",
                playlistUrl = targetPlaylist.url,
                url = "https://target.example/live",
            )
            database.playlistDao().insertOrReplace(targetPlaylist)
            database.channelDao().insertOrReplace(existingChannel)

            val providerAccount = providerAccount()
            val providerPlaylist = Playlist(
                title = "Restored provider",
                url = providerAccount.playlistUrl,
                source = DataSource.Provider,
            )
            val providerChannel = Channel(
                id = COLLIDING_CHANNEL_ID + 1,
                title = "Provider channel",
                category = "Live",
                playlistUrl = providerPlaylist.url,
                url = Channel.URL_DYNAMIC,
                relationId = REFERENCED_REMOTE_CHANNEL_ID,
            )
            val references = listOf("first-item", "second-item").map { itemId ->
                ProviderPlaybackReferenceBackup(
                    channelId = providerChannel.id,
                    accountId = providerAccount.id,
                    providerId = providerAccount.providerId,
                    itemId = itemId,
                    mediaSourceId = "source",
                    sourceType = "live",
                )
            }
            val backup = createBackup(
                context = context,
                records = buildList {
                    add(
                        BackupOrRestoreContracts.wrapPlaylist(
                            json.encodeToString(providerPlaylist)
                        )
                    )
                    add(
                        BackupOrRestoreContracts.wrapChannel(
                            json.encodeToString(providerChannel)
                        )
                    )
                    add(
                        BackupOrRestoreContracts.wrapProviderAccount(
                            json.encodeToString(
                                requireNotNull(ProviderAccountBackup.fromEntity(providerAccount))
                            )
                        )
                    )
                    references.forEach { reference ->
                        add(
                            BackupOrRestoreContracts.wrapPlaybackReference(
                                json.encodeToString(reference)
                            )
                        )
                    }
                },
            )

            val failure = try {
                repository.restoreOrThrow(Uri.fromFile(backup))
                null
            } catch (error: IllegalArgumentException) {
                error
            } finally {
                backup.delete()
            }

            assertNotNull(failure)
            assertEquals(existingChannel, database.channelDao().get(COLLIDING_CHANNEL_ID))
            assertTrue(database.channelDao().getByPlaylistUrl(providerPlaylist.url).isEmpty())
            assertNull(database.playlistDao().get(providerPlaylist.url))
            assertNull(database.providerDao().getAccount(providerAccount.id))
            assertTrue(database.providerDao().getPlaybackReferences().isEmpty())
        }
    }

    private suspend fun withTestRepository(
        block: suspend (M3UDatabase, PlaylistRepositoryImpl, Context) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val client = OkHttpClient()
        val repository = PlaylistRepositoryImpl(
            playlistDao = database.playlistDao(),
            channelDao = database.channelDao(),
            providerDao = database.providerDao(),
            database = database,
            providerLifecycleCoordinator = ProviderLifecycleCoordinator(),
            programmeDao = database.programmeDao(),
            okHttpClient = client,
            m3uParser = M3UParserImpl(),
            xtreamParser = XtreamParserImpl(client),
            workManager = WorkManager.getInstance(context),
            context = context,
            settings = context.settings,
            subscriptionProviderRepository = UnusedSubscriptionProviderRepository,
            extensionContributionScheduler = NoOpExtensionContributionScheduler,
            extensionContributionRunCoordinator = ExtensionContributionRunCoordinator(),
        )
        try {
            block(database, repository, context)
        } finally {
            database.close()
        }
    }

    private fun createBackup(
        context: Context,
        records: List<String>,
    ): File = File.createTempFile("provider-restore-test-", ".backup", context.cacheDir).apply {
        bufferedWriter().use { writer ->
            records.forEach(writer::appendLine)
        }
    }

    private fun providerAccount() = ProviderAccount(
        id = "restored-account",
        providerId = "builtin.media-server",
        providerKind = "emby",
        baseUrl = "https://provider.example",
        serverId = "server",
        serverName = "Provider",
        serverVersion = "1",
        userId = "user",
        username = "viewer",
        playlistUrl = "m3u-provider://account/restored-account/live",
    )

    private data object UnusedSubscriptionProviderRepository :
        SubscriptionProviderRepository {
        override suspend fun discoverProviders(): List<DiscoveredSubscriptionProvider> =
            emptyList()

        override fun observeAccountSummaries(): Flow<List<ProviderAccountSummary>> = emptyFlow()

        override fun stageCredential(secret: String): CredentialHandle = unused()

        override suspend fun subscribe(
            request: ProviderSubscriptionRequest,
        ): ProviderSubscriptionResult = unused()

        override suspend fun refresh(
            playlistUrl: String,
            reason: SubscriptionRefreshReason,
        ): ProviderSubscriptionResult = unused()

        override suspend fun resolvePlayback(channelId: Int): ProviderPlaybackSource? = unused()

        override suspend fun closePlayback(
            session: ProviderPlaybackSession,
            reason: ProviderPlaybackCloseReason,
        ): Boolean = unused()

        override suspend fun removeAccount(playlistUrl: String): Unit = unused()

        override suspend fun invalidateUndecryptableCredentials(): Int = unused()

        override suspend fun closeOrphanedPlaybackSessions(
            afterCreatedAtEpochMillis: Long?,
            afterSessionId: String?,
        ): ProviderSessionCleanupResult = unused()

        private fun unused(): Nothing = error("Not used by backup restore tests")
    }

    private data object NoOpExtensionContributionScheduler :
        ExtensionContributionScheduler {
        override suspend fun enqueue(playlistUrl: String) = Unit

        override suspend fun cancel(playlistUrl: String) = Unit
    }

    private companion object {
        const val COLLIDING_CHANNEL_ID = 41
        const val ORDINARY_CHANNEL_ID = 42
        const val PROVIDER_CHANNEL_BATCH_SIZE = 400
        const val FIRST_NON_COLLIDING_PROVIDER_CHANNEL_ID = 1_000
        const val REFERENCED_REMOTE_CHANNEL_ID = "remote-channel"
    }
}
