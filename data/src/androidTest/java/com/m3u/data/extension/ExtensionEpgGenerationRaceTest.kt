package com.m3u.data.extension

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.repository.extension.ExtensionEpgRefreshContribution
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.security.CredentialHandle
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
class ExtensionEpgGenerationRaceTest {
    private lateinit var database: M3UDatabase
    private lateinit var importer: SubscriptionProviderImporter

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
            database.channelDao().insertOrReplace(
                Channel(
                    url = Channel.URL_DYNAMIC,
                    category = "News",
                    title = "Channel",
                    playlistUrl = PLAYLIST_URL,
                    relationId = CHANNEL_REFERENCE,
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resultCapturedBeforeClearCannotRestoreClearedExtensionEpg() = runBlocking {
        val staleGeneration = importer.captureExtensionEpgRefreshGeneration()
        val staleResult = contribution(EXTENSION_ID, "Stale programme")

        importer.clearExtensionEpg(EXTENSION_ID)

        assertEquals(
            0,
            importer.replaceExtensionEpg(
                playlistUrl = PLAYLIST_URL,
                refreshes = listOf(staleResult),
                refreshGeneration = staleGeneration,
            ),
        )
        assertFalse(extensionSources().any { source -> source.contains(EXTENSION_ID.value) })
        assertTrue(database.programmeDao().observeAll().first().isEmpty())

        val freshGeneration = importer.captureExtensionEpgRefreshGeneration()
        assertEquals(
            1,
            importer.replaceExtensionEpg(
                playlistUrl = PLAYLIST_URL,
                refreshes = listOf(contribution(EXTENSION_ID, "Fresh programme")),
                refreshGeneration = freshGeneration,
            ),
        )
        assertEquals(
            listOf("Fresh programme"),
            database.programmeDao().observeAll().first().map { programme -> programme.title },
        )
    }

    @Test
    fun clearInvalidatesOnlyTheClearedExtensionGeneration() = runBlocking {
        val otherExtensionId = ExtensionId("com.m3u.other.provider")
        val generation = importer.captureExtensionEpgRefreshGeneration()

        importer.clearExtensionEpg(EXTENSION_ID)

        assertEquals(
            1,
            importer.replaceExtensionEpg(
                playlistUrl = PLAYLIST_URL,
                refreshes = listOf(
                    contribution(EXTENSION_ID, "Stale programme"),
                    contribution(otherExtensionId, "Other programme"),
                ),
                refreshGeneration = generation,
            ),
        )
        assertFalse(extensionSources().any { source -> source.contains(EXTENSION_ID.value) })
        assertTrue(extensionSources().any { source -> source.contains(otherExtensionId.value) })
        assertEquals(
            listOf("Other programme"),
            database.programmeDao().observeAll().first().map { programme -> programme.title },
        )
    }

    private suspend fun extensionSources(): List<String> = database.playlistDao()
        .get(PLAYLIST_URL)
        ?.epgUrls
        .orEmpty()
        .filter { source -> source.startsWith("m3u-extension-epg://") }

    private fun contribution(
        extensionId: ExtensionId,
        title: String,
    ) = ExtensionEpgRefreshContribution(
        extensionId = extensionId,
        programmes = listOf(
            ExtensionProgramme(
                channelReference = CHANNEL_REFERENCE,
                title = title,
                startEpochMillis = 1_000,
                endEpochMillis = 2_000,
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
        const val NORMAL_EPG_URL = "https://example.test/guide.xml"
        const val CHANNEL_REFERENCE = "channel-42"
        val EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
    }
}
