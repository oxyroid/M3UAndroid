package com.m3u.data.repository.playlist

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
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
import com.m3u.data.parser.xtream.XtreamChannelInfo
import com.m3u.data.parser.xtream.XtreamData
import com.m3u.data.parser.xtream.XtreamInfo
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamLive
import com.m3u.data.parser.xtream.XtreamOutput
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.data.parser.xtream.XtreamParserImpl
import com.m3u.data.repository.BackupOrRestoreContracts
import com.m3u.data.repository.ProviderAccountBackup
import com.m3u.data.repository.ProviderPlaybackReferenceBackup
import com.m3u.data.repository.channel.ChannelRepositoryImpl
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
import com.m3u.data.worker.hashedWorkTag
import com.m3u.data.worker.playlistWorkTag
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryProviderRestoreTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun playlistTitleUpdatesTrimWhitespaceAndIgnoreBlank() = runBlocking {
        withTestRepository { database, repository, _ ->
            val playlist = Playlist(
                title = "Original",
                url = "https://playlist.example/live.m3u",
                source = DataSource.M3U,
            )
            database.playlistDao().insertOrReplace(playlist)

            repository.onUpdatePlaylistTitle(
                url = playlist.url,
                title = " \n Evening channels \t ",
            )

            assertEquals(
                "Evening channels",
                database.playlistDao().get(playlist.url)?.title,
            )

            repository.onUpdatePlaylistTitle(
                url = playlist.url,
                title = " \n\t ",
            )

            assertEquals(
                "Evening channels",
                database.playlistDao().get(playlist.url)?.title,
            )
        }
    }

    @Test
    fun xtreamPlaylistRefreshEnqueuesSubscriptionWork() = runBlocking {
        withTestRepository { database, repository, context ->
            val input = XtreamInput(
                basicUrl = "https://refresh.example",
                username = "viewer",
                password = "secret",
                type = DataSource.Xtream.TYPE_LIVE,
            )
            val playlistUrl = XtreamInput.encodeToPlaylistUrl(
                input = input,
                serverProtocol = "https",
                port = 443,
            )
            database.playlistDao().insertOrReplace(
                Playlist(
                    title = "Refreshable Xtream",
                    url = playlistUrl,
                    source = DataSource.Xtream,
                )
            )
            val workManager = WorkManager.getInstance(context)

            try {
                repository.refresh(playlistUrl)

                val workInfos = workManager
                    .getWorkInfosByTag(playlistWorkTag(playlistUrl))
                    .get(5, TimeUnit.SECONDS)
                assertTrue(
                    workInfos.any { workInfo ->
                        DataSource.Xtream.value in workInfo.tags
                    }
                )
            } finally {
                workManager.cancelAllWorkByTag(playlistWorkTag(playlistUrl))
                    .result
                    .get(5, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    fun mutablePlaylistAndChannelStateWaitsForMaintenance() = runBlocking {
        withTestRepository { database, repository, context ->
            val playlist = Playlist(
                title = "Original",
                url = "https://playlist.example/live.m3u",
                source = DataSource.M3U,
            )
            val epg = Playlist(
                title = "Guide",
                url = "https://playlist.example/guide.xml",
                source = DataSource.EPG,
            )
            val channel = Channel(
                id = 55,
                title = "News",
                category = "News",
                playlistUrl = playlist.url,
                url = "https://playlist.example/news.ts",
                relationId = "news",
            )
            database.playlistDao().insertOrReplaceAll(playlist, epg)
            database.channelDao().insertOrReplace(channel)
            val channelRepository = ChannelRepositoryImpl(
                channelDao = database.channelDao(),
                playlistDao = database.playlistDao(),
                settings = context.settings,
            )
            val maintenanceEntered = CompletableDeferred<Unit>()
            val releaseMaintenance = CompletableDeferred<Unit>()
            val maintenance = async(Dispatchers.Default) {
                PlaylistDataMaintenanceCoordinator.withExclusive {
                    maintenanceEntered.complete(Unit)
                    releaseMaintenance.await()
                }
            }
            maintenanceEntered.await()

            val mutations = listOf(
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    repository.pinOrUnpinCategory(playlist.url, "News")
                },
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    repository.hideOrUnhideCategory(playlist.url, "Sports")
                },
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    repository.onUpdatePlaylistTitle(playlist.url, "Updated")
                },
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    repository.onUpdatePlaylistUserAgent(playlist.url, "Test agent")
                },
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    repository.onUpdateEpgPlaylist(
                        PlaylistRepository.EpgPlaylistUseCase.Check(
                            playlistUrl = playlist.url,
                            epgUrl = epg.url,
                            action = true,
                        )
                    )
                },
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    repository.onUpdatePlaylistAutoRefreshProgrammes(playlist.url)
                },
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    channelRepository.favouriteOrUnfavourite(channel.id)
                },
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    channelRepository.hide(channel.id, target = true)
                },
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    channelRepository.reportPlayed(channel.id)
                },
            )

            try {
                assertEquals(playlist, database.playlistDao().get(playlist.url))
                assertEquals(channel, database.channelDao().get(channel.id))
                database.channelDao().delete(channel)
                database.channelDao().insertOrReplace(
                    channel.copy(
                        id = 56,
                        url = "https://playlist.example/refreshed-news.ts",
                    )
                )
            } finally {
                releaseMaintenance.complete(Unit)
            }
            withTimeout(5_000) {
                mutations.awaitAll()
                maintenance.await()
            }

            val updatedPlaylist = requireNotNull(database.playlistDao().get(playlist.url))
            assertEquals("Updated", updatedPlaylist.title)
            assertEquals(listOf("News"), updatedPlaylist.pinnedCategories)
            assertEquals(listOf("Sports"), updatedPlaylist.hiddenCategories)
            assertEquals("Test agent", updatedPlaylist.userAgent)
            assertEquals(listOf(epg.url), updatedPlaylist.epgUrls)
            assertTrue(updatedPlaylist.autoRefreshProgrammes)

            assertNull(database.channelDao().get(channel.id))
            val updatedChannel = requireNotNull(database.channelDao().get(56))
            assertTrue(updatedChannel.favourite)
            assertTrue(updatedChannel.hidden)
            assertTrue(updatedChannel.seen > 0L)
        }
    }

    @Test
    fun epgAssociationRejectsDuplicatesAndDeletedSources() = runBlocking {
        withTestRepository { database, repository, _ ->
            val playlist = Playlist(
                title = "Channels",
                url = "https://playlist.example/live.m3u",
                source = DataSource.M3U,
            )
            val epg = Playlist(
                title = "Guide",
                url = "https://playlist.example/guide.xml",
                source = DataSource.EPG,
            )
            database.playlistDao().insertOrReplaceAll(playlist, epg)
            val add = PlaylistRepository.EpgPlaylistUseCase.Check(
                playlistUrl = playlist.url,
                epgUrl = epg.url,
                action = true,
            )

            repository.onUpdateEpgPlaylist(add)
            repository.onUpdateEpgPlaylist(add)

            assertEquals(
                listOf(epg.url),
                database.playlistDao().get(playlist.url)?.epgUrls,
            )

            repository.onUpdateEpgPlaylist(add.copy(action = false))
            database.playlistDao().deleteByUrl(epg.url)
            repository.onUpdateEpgPlaylist(add)

            assertTrue(database.playlistDao().get(playlist.url)?.epgUrls.orEmpty().isEmpty())
        }
    }

    @Test
    fun failedXtreamStagingKeepsOldDataAndContributionSchedule() = runBlocking {
        val scheduler = RecordingExtensionContributionScheduler()
        withTestRepository(
            xtreamParser = FailingXtreamParser,
            extensionContributionScheduler = scheduler,
        ) { database, repository, _ ->
            val input = XtreamInput(
                basicUrl = "https://provider.example",
                username = "viewer",
                password = "secret",
                type = DataSource.Xtream.TYPE_LIVE,
            )
            val playlistUrl = XtreamInput.encodeToPlaylistUrl(
                input = input,
                serverProtocol = "https",
                port = 443,
            )
            val playlist = Playlist(
                title = "Existing",
                url = playlistUrl,
                source = DataSource.Xtream,
            )
            val channel = Channel(
                id = 77,
                title = "Existing channel",
                category = "Live",
                playlistUrl = playlistUrl,
                url = "https://provider.example/live/viewer/secret/77.ts",
                relationId = "77",
            )
            database.playlistDao().insertOrReplace(playlist)
            database.channelDao().insertOrReplace(channel)

            val failure = runCatching {
                repository.xtreamOrThrow(
                    title = "Replacement",
                    basicUrl = input.basicUrl,
                    username = input.username,
                    password = input.password,
                    type = input.type,
                    callback = {},
                )
            }.exceptionOrNull()

            assertNotNull(failure)
            assertEquals(playlist, database.playlistDao().get(playlistUrl))
            assertEquals(listOf(channel), database.channelDao().getByPlaylistUrl(playlistUrl))
            assertTrue(scheduler.cancelledPlaylistUrls.isEmpty())
            assertTrue(scheduler.enqueuedPlaylistUrls.isEmpty())
        }
    }

    @Test
    fun unsubscribeByContentUriRemovesItsMigratedCanonicalPlaylist() = runBlocking {
        val scheduler = RecordingExtensionContributionScheduler()
        withTestRepository(
            extensionContributionScheduler = scheduler,
        ) { database, repository, context ->
            val contentUrl = "content://media/external/file/playlist-42"
            val filename = hashedWorkTag(
                namespace = "local-m3u-file",
                value = contentUrl,
            ).substringAfter(':') + ".m3u"
            val ownedFile = File(context.filesDir, "playlists/$filename")
            val ownedDirectory = checkNotNull(ownedFile.parentFile)
            check(ownedDirectory.exists() || ownedDirectory.mkdirs())
            ownedFile.writeText("#EXTM3U")
            val canonicalUrl = ownedFile.toUri().toString()
            val playlist = Playlist(
                title = "Local playlist",
                url = canonicalUrl,
                source = DataSource.M3U,
            )
            val channel = Channel(
                id = 78,
                title = "Local channel",
                category = "Live",
                playlistUrl = canonicalUrl,
                url = "https://stream.example/78",
                relationId = "78",
            )
            database.playlistDao().insertOrReplace(playlist)
            database.channelDao().insertOrReplace(channel)

            val removed = repository.unsubscribe(contentUrl)

            assertEquals(playlist, removed)
            assertNull(database.playlistDao().get(canonicalUrl))
            assertTrue(database.channelDao().getByPlaylistUrl(canonicalUrl).isEmpty())
            assertFalse(ownedFile.exists())
            assertEquals(
                setOf(contentUrl, canonicalUrl),
                scheduler.cancelledPlaylistUrls.toSet(),
            )
            assertTrue(scheduler.enqueuedPlaylistUrls.isEmpty())
        }
    }

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

            val providerAccount = providerAccount().copy(providerKind = "jellyfin")
            val providerPlaylist = Playlist(
                title = "Restored provider",
                url = providerAccount.playlistUrl,
                source = DataSource.Jellyfin,
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
            assertEquals(
                providerPlaylist.copy(source = DataSource.Provider),
                database.playlistDao().get(providerPlaylist.url),
            )
            assertEquals(
                providerAccount.copy(
                    baseUrl = "https://provider.example/",
                    requiresReauthentication = true,
                ),
                database.providerDao().getAccount(providerAccount.id),
            )
            assertNull(database.providerDao().getCredential(providerAccount.id))
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
            assertEquals(reference.providerId, restoredReference?.providerId)
            assertEquals(reference.itemId, restoredReference?.itemId)
            assertEquals(reference.mediaSourceId, restoredReference?.mediaSourceId)
            assertEquals(reference.sourceType, restoredReference?.sourceType)
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
        xtreamParser: XtreamParser? = null,
        extensionContributionScheduler: ExtensionContributionScheduler =
            NoOpExtensionContributionScheduler,
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
            xtreamParser = xtreamParser ?: XtreamParserImpl(client),
            workManager = WorkManager.getInstance(context),
            context = context,
            settings = context.settings,
            subscriptionProviderRepository = UnusedSubscriptionProviderRepository,
            extensionContributionScheduler = extensionContributionScheduler,
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
        override suspend fun discoverProviders(
            localeTag: String?,
        ): List<DiscoveredSubscriptionProvider> =
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

    private class RecordingExtensionContributionScheduler :
        ExtensionContributionScheduler {
        val enqueuedPlaylistUrls = mutableListOf<String>()
        val cancelledPlaylistUrls = mutableListOf<String>()

        override suspend fun enqueue(playlistUrl: String) {
            enqueuedPlaylistUrls += playlistUrl
        }

        override suspend fun cancel(playlistUrl: String) {
            cancelledPlaylistUrls += playlistUrl
        }
    }

    private data object FailingXtreamParser : XtreamParser {
        override suspend fun getSeriesInfoOrThrow(
            input: XtreamInput,
            seriesId: Int,
        ): XtreamChannelInfo = error("Not used")

        override fun parse(input: XtreamInput): Flow<XtreamData> = flow {
            emit(
                XtreamLive(
                    categoryId = 1,
                    epgChannelId = "new-79",
                    name = "Partially received channel",
                    streamIcon = null,
                    streamId = 79,
                    streamType = "live",
                )
            )
            error("Required Xtream endpoint failed")
        }

        override suspend fun getInfo(input: XtreamInput): XtreamInfo = error("Not used")

        override suspend fun getXtreamOutput(input: XtreamInput): XtreamOutput = XtreamOutput(
            allowedOutputFormats = listOf("ts"),
            serverProtocol = "https",
            port = 443,
        )
    }

    private companion object {
        const val COLLIDING_CHANNEL_ID = 41
        const val ORDINARY_CHANNEL_ID = 42
        const val PROVIDER_CHANNEL_BATCH_SIZE = 400
        const val FIRST_NON_COLLIDING_PROVIDER_CHANNEL_ID = 1_000
        const val REFERENCED_REMOTE_CHANNEL_ID = "remote-channel"
    }
}
