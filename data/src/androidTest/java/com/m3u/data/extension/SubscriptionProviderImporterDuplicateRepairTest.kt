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
import com.m3u.data.extension.security.CredentialVault
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionProviderImporterDuplicateRepairTest {
    private lateinit var database: M3UDatabase
    private lateinit var importer: SubscriptionProviderImporter

    @Before
    fun setUp() {
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshedDuplicateKeepsCanonicalLocalStateAndUsesFreshRemoteData() = runBlocking {
        seedAccount()
        val canonical = channel(
            id = 101,
            title = "Canonical old title",
            category = "Canonical old category",
            favourite = false,
            hidden = true,
            seen = 5,
            relationId = REFRESHED_REMOTE_ID,
        )
        val duplicate = channel(
            id = 102,
            title = "Duplicate old title",
            category = "Duplicate old category",
            favourite = true,
            hidden = false,
            seen = 42,
            relationId = REFRESHED_REMOTE_ID,
        )
        database.channelDao().insertOrReplace(canonical)
        database.channelDao().insertOrReplace(duplicate)
        database.providerDao().insertOrReplace(
            playbackReference(channelId = canonical.id, itemId = "canonical-old-item")
        )
        database.providerDao().insertOrReplace(
            playbackReference(channelId = duplicate.id, itemId = "duplicate-old-item")
        )

        assertEquals(
            1,
            importer.refresh(
                account = ACCOUNT,
                refresh = refresh(
                    descriptor(
                        remoteId = REFRESHED_REMOTE_ID,
                        title = "Fresh title",
                        category = "Fresh category",
                        itemId = "fresh-item",
                        mediaSourceId = "fresh-media-source",
                        logoUrl = "https://provider.example.test/fresh.png",
                    )
                ),
            ),
        )

        val repaired = database.channelDao().getByPlaylistUrl(PLAYLIST_URL).single()
        assertEquals(canonical.id, repaired.id)
        assertEquals(Channel.URL_DYNAMIC, repaired.url)
        assertEquals("Fresh title", repaired.title)
        assertEquals("Fresh category", repaired.category)
        assertEquals("https://provider.example.test/fresh.png", repaired.cover)
        assertTrue(repaired.favourite)
        assertTrue(repaired.hidden)
        assertEquals(42L, repaired.seen)
        assertNull(database.channelDao().get(duplicate.id))
        assertEquals(
            ChannelPlaybackReference(
                channelId = canonical.id,
                accountId = ACCOUNT_ID,
                providerId = PROVIDER_ID.value,
                itemId = "fresh-item",
                mediaSourceId = "fresh-media-source",
                sourceType = SOURCE_TYPE,
            ),
            database.providerDao().getPlaybackReference(canonical.id),
        )
        assertNull(database.providerDao().getPlaybackReference(duplicate.id))
    }

    @Test
    fun staleDuplicateTransfersDeterministicValidReferenceToMergedFavourite() = runBlocking {
        seedAccount()
        val canonical = channel(
            id = 201,
            title = "Canonical stale title",
            category = "Canonical stale category",
            favourite = false,
            hidden = false,
            seen = 3,
            relationId = STALE_REMOTE_ID,
        )
        val favouriteDuplicate = channel(
            id = 202,
            title = "Favourite duplicate",
            category = "Duplicate category",
            favourite = true,
            hidden = false,
            seen = 77,
            relationId = STALE_REMOTE_ID,
        )
        val hiddenDuplicate = channel(
            id = 203,
            title = "Hidden duplicate",
            category = "Duplicate category",
            favourite = false,
            hidden = true,
            seen = 55,
            relationId = STALE_REMOTE_ID,
        )
        // Seed the inconsistent rows directly, as an old database can contain duplicates that
        // predate the metadata-base synchronization performed by the normal DAO entry points.
        database.channelDao().insertOrReplaceAllRaw(
            canonical,
            favouriteDuplicate,
            hiddenDuplicate,
        )
        database.providerDao().insertOrReplace(
            playbackReference(
                channelId = favouriteDuplicate.id,
                itemId = "first-valid-stale-item",
            )
        )
        database.providerDao().insertOrReplace(
            playbackReference(
                channelId = hiddenDuplicate.id,
                itemId = "second-valid-stale-item",
            )
        )

        importer.refresh(
            account = ACCOUNT,
            refresh = refresh(
                descriptor(
                    remoteId = OTHER_REMOTE_ID,
                    title = "Other channel",
                    category = "Other",
                    itemId = "other-item",
                )
            ),
        )

        val stale = database.channelDao().get(canonical.id)
        assertEquals("Canonical stale title", stale?.title)
        assertEquals("Canonical stale category", stale?.category)
        assertTrue(stale?.favourite == true)
        assertTrue(stale?.hidden == true)
        assertEquals(77L, stale?.seen)
        assertNull(database.channelDao().get(favouriteDuplicate.id))
        assertNull(database.channelDao().get(hiddenDuplicate.id))
        assertEquals(
            playbackReference(
                channelId = canonical.id,
                itemId = "first-valid-stale-item",
            ),
            database.providerDao().getPlaybackReference(canonical.id),
        )
        assertNull(database.providerDao().getPlaybackReference(favouriteDuplicate.id))
        assertNull(database.providerDao().getPlaybackReference(hiddenDuplicate.id))
        assertEquals(
            setOf(STALE_REMOTE_ID, OTHER_REMOTE_ID),
            database.channelDao().getByPlaylistUrl(PLAYLIST_URL)
                .mapNotNullTo(mutableSetOf(), Channel::relationId),
        )
    }

    private suspend fun seedAccount() {
        database.playlistDao().insertOrReplace(
            Playlist(
                title = "Provider",
                url = PLAYLIST_URL,
                source = DataSource.Provider,
            )
        )
        database.providerDao().insertOrReplace(ACCOUNT)
    }

    private fun channel(
        id: Int,
        title: String,
        category: String,
        favourite: Boolean,
        hidden: Boolean,
        seen: Long,
        relationId: String,
    ) = Channel(
        id = id,
        url = Channel.URL_DYNAMIC,
        category = category,
        title = title,
        playlistUrl = PLAYLIST_URL,
        favourite = favourite,
        hidden = hidden,
        seen = seen,
        relationId = relationId,
    )

    private fun playbackReference(
        channelId: Int,
        itemId: String,
    ) = ChannelPlaybackReference(
        channelId = channelId,
        accountId = ACCOUNT_ID,
        providerId = PROVIDER_ID.value,
        itemId = itemId,
        mediaSourceId = null,
        sourceType = SOURCE_TYPE,
    )

    private fun refresh(
        vararg channels: SubscriptionChannelDescriptor,
    ) = SubscriptionContentRefreshResult(
        source = SubscriptionSourceDescriptor(
            remoteId = SERVER_ID,
            providerKind = PROVIDER_KIND,
        ),
        channels = channels.toList(),
    )

    private fun descriptor(
        remoteId: String,
        title: String,
        category: String,
        itemId: String,
        mediaSourceId: String? = null,
        logoUrl: String? = null,
    ) = SubscriptionChannelDescriptor(
        remoteId = remoteId,
        title = title,
        category = category,
        logoUrl = logoUrl,
        playbackReference = PlaybackReference(
            providerId = PROVIDER_ID,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            sourceType = SOURCE_TYPE,
        ),
    )

    private data object UnusedCredentialVault : CredentialVault {
        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ): ProviderCredentialEntity = error("Not used")

        override fun decrypt(
            credential: ProviderCredentialEntity,
        ): String? = error("Not used")

        override fun stage(secret: String): CredentialHandle = error("Not used")

        override fun consume(handle: CredentialHandle): String? = error("Not used")
    }

    private companion object {
        val PROVIDER_ID = ExtensionId("provider.duplicate-repair")
        val PROVIDER_KIND = ProviderKind("duplicate-repair")
        const val ACCOUNT_ID = "duplicate-repair-account"
        const val PLAYLIST_URL = "provider://duplicate-repair/account"
        const val SERVER_ID = "duplicate-repair-server"
        const val REFRESHED_REMOTE_ID = "remote-refreshed"
        const val STALE_REMOTE_ID = "remote-stale"
        const val OTHER_REMOTE_ID = "remote-other"
        const val SOURCE_TYPE = "live"
        val ACCOUNT = ProviderAccount(
            id = ACCOUNT_ID,
            providerId = PROVIDER_ID.value,
            providerKind = PROVIDER_KIND.value,
            baseUrl = "https://provider.example.test",
            serverId = SERVER_ID,
            serverName = "Provider",
            serverVersion = "1",
            userId = "user",
            username = "user",
            playlistUrl = PLAYLIST_URL,
        )
    }
}
