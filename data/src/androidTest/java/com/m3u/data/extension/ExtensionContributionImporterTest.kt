package com.m3u.data.extension

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.ProviderPlaybackSessionEntity
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.repository.extension.ExtensionEpgRefreshContribution
import com.m3u.data.repository.extension.ExtensionMetadataRefreshContribution
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionContributionImporterTest {
    private lateinit var database: M3UDatabase
    private lateinit var importer: SubscriptionProviderImporter
    private var channelId: Int = 0

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            importer = SubscriptionProviderImporter(
                database = database,
                playlistDao = database.playlistDao(),
                channelDao = database.channelDao(),
                providerDao = database.providerDao(),
                programmeDao = database.programmeDao(),
                credentialVault = UnusedCredentialVault,
            )
            database.playlistDao().insertOrReplace(
                Playlist(
                    title = "Provider",
                    url = PLAYLIST_URL,
                    source = DataSource.Provider,
                    epgUrls = listOf(NORMAL_EPG_URL),
                )
            )
            database.providerDao().insertOrReplace(
                ProviderAccount(
                    id = PROVIDER_ACCOUNT_ID,
                    providerId = EXTENSION_ID.value,
                    providerKind = "reference",
                    baseUrl = "https://example.test",
                    serverId = "server-1",
                    serverName = "Reference server",
                    serverVersion = "1.0",
                    userId = "user-1",
                    username = "viewer",
                    playlistUrl = PLAYLIST_URL,
                )
            )
            database.providerDao().insertOrReplace(
                ProviderCredentialEntity(
                    accountId = PROVIDER_ACCOUNT_ID,
                    credentialHandle = CREDENTIAL_HANDLE,
                    ciphertext = "ciphertext",
                    nonce = "nonce",
                    keyVersion = 1,
                )
            )
            channelId = database.channelDao().insertOrReplace(
                Channel(
                    url = Channel.URL_DYNAMIC,
                    category = "Original category",
                    title = "Original title",
                    playlistUrl = PLAYLIST_URL,
                    favourite = true,
                    hidden = true,
                    relationId = CHANNEL_REFERENCE,
                )
            ).toInt()
            database.providerDao().insertOrReplace(
                ChannelPlaybackReference(
                    channelId = channelId,
                    accountId = PROVIDER_ACCOUNT_ID,
                    providerId = EXTENSION_ID.value,
                    itemId = PLAYBACK_ITEM_ID,
                    mediaSourceId = null,
                    sourceType = "live",
                )
            )
            database.providerDao().insertOrReplace(
                ProviderPlaybackSessionEntity(
                    id = PLAYBACK_SESSION_ID,
                    accountId = PROVIDER_ACCOUNT_ID,
                    providerId = EXTENSION_ID.value,
                    itemId = PLAYBACK_ITEM_ID,
                    mediaSourceId = null,
                    sourceType = "live",
                    playSessionId = "remote-session-1",
                    liveStreamId = "live-stream-1",
                    createdAtEpochMillis = 1_000,
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun metadataPatchUpdatesOnlyKnownHostChannelAndPreservesLocalState() = runBlocking {
        val count = importer.applyMetadataEnrichment(
            PLAYLIST_URL,
            listOf(
                ExtensionMetadataRefreshContribution(
                    EXTENSION_ID,
                    listOf(
                        ChannelMetadataPatch(
                            stableReference = CHANNEL_REFERENCE,
                            title = "Enriched title",
                            category = "Enriched category",
                        ),
                        ChannelMetadataPatch(stableReference = "unknown", title = "Injected"),
                    ),
                ),
            ),
        )

        val channel = database.channelDao()
            .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
        assertEquals(1, count)
        assertEquals("Enriched title", channel?.title)
        assertEquals("Enriched category", channel?.category)
        assertTrue(channel?.favourite == true)
        assertTrue(channel?.hidden == true)
        val sourceChannel = database.channelDao().getAllWithSourceMetadata()
            .single { item ->
                item.playlistUrl == PLAYLIST_URL && item.relationId == CHANNEL_REFERENCE
            }
        assertEquals("Original title", sourceChannel.title)
        assertEquals("Original category", sourceChannel.category)
    }

    @Test
    fun metadataOverlaysUseDeterministicPriorityAndEmptySuccessClearsOnlyItsOwner() =
        runBlocking {
            val firstExtension = ExtensionId("com.m3u.a-metadata")
            val secondExtension = ExtensionId("com.m3u.z-metadata")
            importer.applyMetadataEnrichment(
                PLAYLIST_URL,
                listOf(
                    metadataRefresh(
                        extensionId = secondExtension,
                        title = "Second title",
                        category = "Second category",
                    ),
                    metadataRefresh(
                        extensionId = firstExtension,
                        title = "First title",
                        category = null,
                    ),
                ),
            )

            var channel = database.channelDao()
                .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
            assertEquals("First title", channel?.title)
            assertEquals("Second category", channel?.category)

            importer.applyMetadataEnrichment(
                PLAYLIST_URL,
                listOf(ExtensionMetadataRefreshContribution(firstExtension, emptyList())),
            )

            channel = database.channelDao()
                .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
            assertEquals("Second title", channel?.title)
            assertEquals("Second category", channel?.category)

            assertEquals(1, importer.clearExtensionMetadata(secondExtension))
            channel = database.channelDao()
                .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
            assertEquals("Original title", channel?.title)
            assertEquals("Original category", channel?.category)
        }

    @Test
    fun providerRefreshUpdatesSourceBaseWithoutDiscardingOrSolidifyingOverlay() = runBlocking {
        importer.applyMetadataEnrichment(
            PLAYLIST_URL,
            listOf(
                metadataRefresh(
                    extensionId = EXTENSION_ID,
                    title = "Overlay title",
                    category = "Overlay category",
                )
            ),
        )

        val account = requireNotNull(database.providerDao().getAccount(PROVIDER_ACCOUNT_ID))
        importer.refresh(
            account = account,
            refresh = providerRefresh(
                title = "Updated source title",
                category = "Updated source category",
            ),
        )

        var channel = database.channelDao()
            .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
        assertEquals("Overlay title", channel?.title)
        assertEquals("Overlay category", channel?.category)
        assertEquals(
            ChannelMetadataSnapshot(
                stableReference = CHANNEL_REFERENCE,
                title = "Updated source title",
                category = "Updated source category",
            ),
            importer.metadataSnapshots(PLAYLIST_URL).single(),
        )

        importer.clearExtensionMetadata(EXTENSION_ID)
        channel = database.channelDao()
            .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
        assertEquals("Updated source title", channel?.title)
        assertEquals("Updated source category", channel?.category)
    }

    @Test
    fun playlistUpsertDoesNotCascadeAwayMetadataOwnership() = runBlocking {
        importer.applyMetadataEnrichment(
            PLAYLIST_URL,
            listOf(metadataRefresh(EXTENSION_ID, "Overlay title", "Overlay category")),
        )

        val playlist = requireNotNull(database.playlistDao().get(PLAYLIST_URL))
        database.playlistDao().insertOrReplace(playlist.copy(title = "Renamed provider"))

        assertEquals(1, importer.clearExtensionMetadata(EXTENSION_ID))
        val channel = database.channelDao()
            .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
        assertEquals("Original title", channel?.title)
        assertEquals("Original category", channel?.category)
        assertEquals(
            PROVIDER_ACCOUNT_ID,
            database.providerDao().getAccount(PROVIDER_ACCOUNT_ID)?.id,
        )
    }

    @Test
    fun playlistUrlMigrationMovesSourceBaseAndOverlayTogether() = runBlocking {
        importer.applyMetadataEnrichment(
            PLAYLIST_URL,
            listOf(metadataRefresh(EXTENSION_ID, "Overlay title", "Overlay category")),
        )

        database.playlistDao().updateUrl(PLAYLIST_URL, MIGRATED_PLAYLIST_URL)

        var channel = database.channelDao()
            .getByPlaylistUrlAndRelationId(MIGRATED_PLAYLIST_URL, CHANNEL_REFERENCE)
        assertEquals("Overlay title", channel?.title)
        assertEquals(
            MIGRATED_PLAYLIST_URL,
            database.providerDao().getAccount(PROVIDER_ACCOUNT_ID)?.playlistUrl,
        )

        assertEquals(1, importer.clearExtensionMetadata(EXTENSION_ID))
        channel = database.channelDao()
            .getByPlaylistUrlAndRelationId(MIGRATED_PLAYLIST_URL, CHANNEL_REFERENCE)
        assertEquals("Original title", channel?.title)
        assertEquals("Original category", channel?.category)
    }

    @Test
    fun deleteThenReimportKeepsOverlayButOrphanCleanupPreventsLaterResurrection() = runBlocking {
        importer.applyMetadataEnrichment(
            PLAYLIST_URL,
            listOf(metadataRefresh(EXTENSION_ID, "Overlay title", "Overlay category")),
        )

        database.channelDao().deleteByPlaylistUrl(PLAYLIST_URL)
        channelId = database.channelDao().insertOrReplace(
            Channel(
                url = Channel.URL_DYNAMIC,
                category = "Reimported category",
                title = "Reimported title",
                playlistUrl = PLAYLIST_URL,
                relationId = CHANNEL_REFERENCE,
            )
        ).toInt()
        database.channelDao().deleteOrphanedMetadata(PLAYLIST_URL)

        var channel = database.channelDao()
            .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
        assertEquals("Overlay title", channel?.title)
        assertEquals("Overlay category", channel?.category)

        database.channelDao().deleteByPlaylistUrl(PLAYLIST_URL)
        assertEquals(1, database.channelDao().deleteOrphanedMetadata(PLAYLIST_URL))
        channelId = database.channelDao().insertOrReplace(
            Channel(
                url = Channel.URL_DYNAMIC,
                category = "Returned category",
                title = "Returned title",
                playlistUrl = PLAYLIST_URL,
                relationId = CHANNEL_REFERENCE,
            )
        ).toInt()

        channel = database.channelDao()
            .getByPlaylistUrlAndRelationId(PLAYLIST_URL, CHANNEL_REFERENCE)
        assertEquals("Returned title", channel?.title)
        assertEquals("Returned category", channel?.category)
        assertEquals(0, importer.clearExtensionMetadata(EXTENSION_ID))
    }

    @Test
    fun epgReplacementUsesIsolatedSourceAndCleansPreviousExtensionData() = runBlocking {
        val count = importer.replaceExtensionEpg(
            PLAYLIST_URL,
            listOf(
                ExtensionEpgRefreshContribution(
                    EXTENSION_ID,
                    listOf(
                        ExtensionProgramme(
                            channelReference = CHANNEL_REFERENCE,
                            title = "Reference programme",
                            startEpochMillis = 1_000,
                            endEpochMillis = 2_000,
                        ),
                        ExtensionProgramme(
                            channelReference = "unknown",
                            title = "Injected programme",
                            startEpochMillis = 1_000,
                            endEpochMillis = 2_000,
                        ),
                    ),
                ),
            ),
        )

        val importedPlaylist = database.playlistDao().get(PLAYLIST_URL)
        val extensionSource = importedPlaylist?.epgUrls
            ?.single { source -> source.startsWith("m3u-extension-epg://") }
        val programmes = database.programmeDao().observeAll().first()
        assertEquals(1, count)
        assertTrue(importedPlaylist?.epgUrls?.contains(NORMAL_EPG_URL) == true)
        assertEquals(CHANNEL_REFERENCE, programmes.single().channelId)
        assertEquals(extensionSource, programmes.single().epgUrl)

        importer.replaceExtensionEpg(
            PLAYLIST_URL,
            listOf(ExtensionEpgRefreshContribution(EXTENSION_ID, emptyList())),
        )

        val cleanedPlaylist = database.playlistDao().get(PLAYLIST_URL)
        assertEquals(listOf(NORMAL_EPG_URL), cleanedPlaylist?.epgUrls)
        assertFalse(database.programmeDao().observeAll().first().any { programme ->
            programme.epgUrl.startsWith("m3u-extension-epg://")
        })
    }

    @Test
    fun epgRefreshOnlyReplacesSuccessfulExtensionAndEmptySuccessClearsItsOwner() = runBlocking {
        val otherExtensionId = ExtensionId("com.m3u.other.provider")
        importer.replaceExtensionEpg(
            PLAYLIST_URL,
            listOf(
                ExtensionEpgRefreshContribution(
                    EXTENSION_ID,
                    listOf(ExtensionProgramme(CHANNEL_REFERENCE, "Reference old", 1_000, 2_000)),
                ),
                ExtensionEpgRefreshContribution(
                    otherExtensionId,
                    listOf(ExtensionProgramme(CHANNEL_REFERENCE, "Other old", 2_000, 3_000)),
                ),
            ),
        )

        importer.replaceExtensionEpg(PLAYLIST_URL, emptyList())
        assertEquals(
            setOf("Reference old", "Other old"),
            database.programmeDao().observeAll().first()
                .mapTo(mutableSetOf()) { programme -> programme.title },
        )

        importer.replaceExtensionEpg(
            PLAYLIST_URL,
            listOf(
                ExtensionEpgRefreshContribution(
                    otherExtensionId,
                    listOf(ExtensionProgramme(CHANNEL_REFERENCE, "Other new", 3_000, 4_000)),
                )
            ),
        )
        assertEquals(
            setOf("Reference old", "Other new"),
            database.programmeDao().observeAll().first()
                .mapTo(mutableSetOf()) { programme -> programme.title },
        )

        importer.replaceExtensionEpg(
            PLAYLIST_URL,
            listOf(ExtensionEpgRefreshContribution(EXTENSION_ID, emptyList())),
        )

        val playlist = database.playlistDao().get(PLAYLIST_URL)
        val programmes = database.programmeDao().observeAll().first()
        assertEquals(listOf("Other new"), programmes.map { programme -> programme.title })
        assertFalse(playlist?.epgUrls.orEmpty().any { source ->
            source.startsWith("m3u-extension-epg://${EXTENSION_ID.value}/")
        })
        assertTrue(playlist?.epgUrls.orEmpty().any { source ->
            source.startsWith("m3u-extension-epg://${otherExtensionId.value}/")
        })
    }

    @Test
    fun clearingOneExtensionEpgPreservesHostAndOtherExtensionSources() = runBlocking {
        val otherExtensionId = ExtensionId("com.m3u.other.provider")
        importer.replaceExtensionEpg(
            PLAYLIST_URL,
            listOf(
                ExtensionEpgRefreshContribution(
                    EXTENSION_ID,
                    listOf(ExtensionProgramme(CHANNEL_REFERENCE, "Reference", 1_000, 2_000)),
                ),
                ExtensionEpgRefreshContribution(
                    otherExtensionId,
                    listOf(ExtensionProgramme(CHANNEL_REFERENCE, "Other", 2_000, 3_000)),
                ),
            ),
        )

        assertEquals(1, importer.clearExtensionEpg(EXTENSION_ID))

        val playlist = database.playlistDao().get(PLAYLIST_URL)
        val remainingSources = playlist?.epgUrls.orEmpty()
        val programmes = database.programmeDao().observeAll().first()
        assertTrue(NORMAL_EPG_URL in remainingSources)
        assertTrue(remainingSources.any { it.startsWith("m3u-extension-epg://${otherExtensionId.value}/") })
        assertFalse(remainingSources.any { it.startsWith("m3u-extension-epg://${EXTENSION_ID.value}/") })
        assertTrue(programmes.all { !it.epgUrl.startsWith("m3u-extension-epg://${EXTENSION_ID.value}/") })
        assertTrue(programmes.any { it.epgUrl.startsWith("m3u-extension-epg://${otherExtensionId.value}/") })
    }

    @Test
    fun replacingAndClearingExtensionEpgPreservesProviderAccountAndCredential() = runBlocking {
        importer.replaceExtensionEpg(
            PLAYLIST_URL,
            listOf(
                ExtensionEpgRefreshContribution(
                    EXTENSION_ID,
                    listOf(ExtensionProgramme(CHANNEL_REFERENCE, "Reference", 1_000, 2_000)),
                )
            ),
        )

        assertProviderOwnershipIsPresent()

        importer.clearExtensionEpg(EXTENSION_ID)

        assertProviderOwnershipIsPresent()
    }

    private suspend fun assertProviderOwnershipIsPresent() {
        assertEquals(PROVIDER_ACCOUNT_ID, database.providerDao().getAccount(PROVIDER_ACCOUNT_ID)?.id)
        assertEquals(
            CREDENTIAL_HANDLE,
            database.providerDao().getCredential(PROVIDER_ACCOUNT_ID)?.credentialHandle,
        )
        assertEquals(
            PLAYBACK_ITEM_ID,
            database.providerDao().getPlaybackReference(channelId)?.itemId,
        )
        assertEquals(
            PLAYBACK_SESSION_ID,
            database.providerDao().getPlaybackSessions().singleOrNull()?.id,
        )
    }

    private fun metadataRefresh(
        extensionId: ExtensionId,
        title: String?,
        category: String?,
    ) = ExtensionMetadataRefreshContribution(
        extensionId = extensionId,
        patches = listOf(
            ChannelMetadataPatch(
                stableReference = CHANNEL_REFERENCE,
                title = title,
                category = category,
            )
        ),
    )

    private fun providerRefresh(
        title: String,
        category: String,
    ) = SubscriptionContentRefreshResult(
        source = SubscriptionSourceDescriptor(
            remoteId = "server-1",
            providerKind = ProviderKind("reference"),
        ),
        channels = listOf(
            SubscriptionChannelDescriptor(
                remoteId = CHANNEL_REFERENCE,
                title = title,
                category = category,
                playbackReference = PlaybackReference(
                    providerId = EXTENSION_ID,
                    itemId = PLAYBACK_ITEM_ID,
                    sourceType = "live",
                ),
            )
        ),
    )

    private object UnusedCredentialVault : CredentialVault {
        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ): ProviderCredentialEntity = error("Not used")

        override fun decrypt(credential: ProviderCredentialEntity): String? = error("Not used")
        override fun stage(secret: String): CredentialHandle = error("Not used")
        override fun consume(handle: CredentialHandle): String? = error("Not used")
    }

    private companion object {
        const val PLAYLIST_URL = "m3u-provider://account/test/live"
        const val MIGRATED_PLAYLIST_URL = "m3u-provider://account/test/migrated"
        const val PROVIDER_ACCOUNT_ID = "provider-account-1"
        const val CREDENTIAL_HANDLE = "credential-handle-1"
        const val PLAYBACK_ITEM_ID = "item-1"
        const val PLAYBACK_SESSION_ID = "session-1"
        const val NORMAL_EPG_URL = "https://example.test/guide.xml"
        const val CHANNEL_REFERENCE = "channel-42"
        val EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
    }
}
