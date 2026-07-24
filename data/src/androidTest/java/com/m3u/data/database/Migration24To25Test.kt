package com.m3u.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration24To25Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = M3UDatabase::class.java,
    )

    @Test
    fun migrationRemovesTransientUrlsAndInvalidatesUnsafeBaseUrls() {
        migrationHelper.createDatabase(DATABASE_NAME, 24).apply {
            insertPlaylist(USERINFO_PLAYLIST_URL, "Userinfo provider")
            insertPlaylist(QUERY_PLAYLIST_URL, "Query provider")
            insertPlaylist(FRAGMENT_PLAYLIST_URL, "Fragment provider")
            insertPlaylist(INVALID_PLAYLIST_URL, "Invalid provider")
            insertPlaylist(INVALID_PROVIDER_ID_PLAYLIST_URL, "Invalid provider id")
            insertPlaylist(INVALID_PROVIDER_KIND_PLAYLIST_URL, "Invalid provider kind")
            insertPlaylist(SAFE_PLAYLIST_URL, "Safe provider")
            insertAccount(
                id = USERINFO_ACCOUNT_ID,
                playlistUrl = USERINFO_PLAYLIST_URL,
                baseUrl = "https://$USERINFO_SECRET:$PASSWORD_SECRET@media.example.test/emby",
            )
            insertAccount(
                id = QUERY_ACCOUNT_ID,
                playlistUrl = QUERY_PLAYLIST_URL,
                baseUrl = "https://media.example.test/emby?api_key=$QUERY_SECRET",
            )
            insertAccount(
                id = FRAGMENT_ACCOUNT_ID,
                playlistUrl = FRAGMENT_PLAYLIST_URL,
                baseUrl = "https://media.example.test/emby#$FRAGMENT_SECRET",
            )
            insertAccount(
                id = INVALID_ACCOUNT_ID,
                playlistUrl = INVALID_PLAYLIST_URL,
                baseUrl = "not a valid provider URL $INVALID_URL_SECRET",
            )
            insertAccount(
                id = SAFE_ACCOUNT_ID,
                playlistUrl = SAFE_PLAYLIST_URL,
                baseUrl = "https://media.example.test/emby",
            )
            insertAccount(
                id = INVALID_PROVIDER_ID_ACCOUNT_ID,
                playlistUrl = INVALID_PROVIDER_ID_PLAYLIST_URL,
                baseUrl = "https://media.example.test/emby",
                providerId = "Invalid.Provider",
            )
            insertAccount(
                id = INVALID_PROVIDER_KIND_ACCOUNT_ID,
                playlistUrl = INVALID_PROVIDER_KIND_PLAYLIST_URL,
                baseUrl = "https://media.example.test/emby",
                providerKind = "Invalid Kind",
            )
            insertCredential(USERINFO_ACCOUNT_ID, "userinfo-credential")
            insertCredential(QUERY_ACCOUNT_ID, "query-credential")
            insertCredential(FRAGMENT_ACCOUNT_ID, "fragment-credential")
            insertCredential(INVALID_ACCOUNT_ID, "invalid-credential")
            insertCredential(SAFE_ACCOUNT_ID, "safe-credential")
            insertSession(
                id = "userinfo-session",
                accountId = USERINFO_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live?token=$FALLBACK_SECRET",
            )
            insertSession(
                id = "query-session",
                accountId = QUERY_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live?token=$FALLBACK_SECRET",
            )
            insertSession(
                id = "fragment-session",
                accountId = FRAGMENT_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live?token=$FALLBACK_SECRET",
            )
            insertSession(
                id = "invalid-session",
                accountId = INVALID_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live?token=$FALLBACK_SECRET",
            )
            insertSession(
                id = "safe-session",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live?token=$FALLBACK_SECRET",
            )
            insertSession(
                id = "mismatched-provider-session",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                providerId = OTHER_PROVIDER_ID,
            )
            insertSession(
                id = "invalid-provider-session",
                accountId = INVALID_PROVIDER_ID_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                providerId = "Invalid.Provider",
            )
            insertSession(
                id = "invalid-provider-kind-session",
                accountId = INVALID_PROVIDER_KIND_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
            )
            insertSession(
                id = "oversized-item-session",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                itemId = MULTIBYTE_OVERSIZED_ID,
            )
            insertSession(
                id = "blank-media-session",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                mediaSourceId = "",
            )
            insertSession(
                id = "oversized-media-session",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                mediaSourceId = MULTIBYTE_OVERSIZED_ID,
            )
            insertSession(
                id = "oversized-source-session",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                sourceType = MULTIBYTE_OVERSIZED_SOURCE_TYPE,
            )
            insertSession(
                id = "missing-remote-session-ids",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                playSessionId = null,
                liveStreamId = null,
            )
            insertSession(
                id = "oversized-remote-session-id",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                playSessionId = MULTIBYTE_OVERSIZED_ID,
                liveStreamId = null,
            )
            insertSession(
                id = "",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
            )
            insertSession(
                id = MULTIBYTE_OVERSIZED_ID,
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
            )
            insertSession(
                id = "negative-created-at-session",
                accountId = SAFE_ACCOUNT_ID,
                fallbackUrl = "https://media.example.test/live",
                createdAtEpochMillis = -1L,
            )
            insertChannelReference(
                channelId = 1,
                accountId = SAFE_ACCOUNT_ID,
                playlistUrl = SAFE_PLAYLIST_URL,
            )
            insertChannelReference(
                channelId = 2,
                accountId = SAFE_ACCOUNT_ID,
                playlistUrl = SAFE_PLAYLIST_URL,
                providerId = OTHER_PROVIDER_ID,
            )
            insertChannelReference(
                channelId = 3,
                accountId = INVALID_PROVIDER_ID_ACCOUNT_ID,
                playlistUrl = INVALID_PROVIDER_ID_PLAYLIST_URL,
                providerId = "Invalid.Provider",
            )
            insertChannelReference(
                channelId = 4,
                accountId = SAFE_ACCOUNT_ID,
                playlistUrl = SAFE_PLAYLIST_URL,
                itemId = MULTIBYTE_OVERSIZED_ID,
            )
            insertChannelReference(
                channelId = 5,
                accountId = SAFE_ACCOUNT_ID,
                playlistUrl = SAFE_PLAYLIST_URL,
                mediaSourceId = "",
            )
            insertChannelReference(
                channelId = 6,
                accountId = SAFE_ACCOUNT_ID,
                playlistUrl = SAFE_PLAYLIST_URL,
                mediaSourceId = MULTIBYTE_OVERSIZED_ID,
            )
            insertChannelReference(
                channelId = 7,
                accountId = SAFE_ACCOUNT_ID,
                playlistUrl = SAFE_PLAYLIST_URL,
                sourceType = MULTIBYTE_OVERSIZED_SOURCE_TYPE,
            )
            insertChannelReference(
                channelId = 8,
                accountId = INVALID_ACCOUNT_ID,
                playlistUrl = INVALID_PLAYLIST_URL,
            )
            insertChannelReference(
                channelId = 9,
                accountId = INVALID_PROVIDER_KIND_ACCOUNT_ID,
                playlistUrl = INVALID_PROVIDER_KIND_PLAYLIST_URL,
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, M3UDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .addMigrations(DatabaseMigrations.MIGRATION_24_25)
            .addMigrations(DatabaseMigrations.MIGRATION_25_26)
            .build()
        val migrated = database.openHelper.writableDatabase

        listOf(USERINFO_ACCOUNT_ID, QUERY_ACCOUNT_ID, FRAGMENT_ACCOUNT_ID).forEach { accountId ->
            assertSanitizedAndInvalidated(migrated, accountId)
        }
        assertRowCount(migrated, "provider_accounts", "id", INVALID_ACCOUNT_ID, 0)
        assertRowCount(migrated, "playlists", "url", INVALID_PLAYLIST_URL, 0)
        assertRowCount(migrated, "streams", "playlist_url", INVALID_PLAYLIST_URL, 0)
        assertRowCount(
            migrated,
            "channel_playback_references",
            "account_id",
            INVALID_ACCOUNT_ID,
            0,
        )
        assertRowCount(migrated, "provider_credentials", "account_id", INVALID_ACCOUNT_ID, 0)
        assertRowCount(
            migrated,
            "provider_playback_sessions",
            "account_id",
            INVALID_ACCOUNT_ID,
            0,
        )
        listOf(
            INVALID_PROVIDER_ID_ACCOUNT_ID to INVALID_PROVIDER_ID_PLAYLIST_URL,
            INVALID_PROVIDER_KIND_ACCOUNT_ID to INVALID_PROVIDER_KIND_PLAYLIST_URL,
        ).forEach { (accountId, playlistUrl) ->
            assertRowCount(migrated, "provider_accounts", "id", accountId, 0)
            assertRowCount(migrated, "playlists", "url", playlistUrl, 0)
            assertRowCount(migrated, "streams", "playlist_url", playlistUrl, 0)
            assertRowCount(migrated, "channel_playback_references", "account_id", accountId, 0)
            assertRowCount(migrated, "provider_playback_sessions", "account_id", accountId, 0)
        }
        migrated.query(
            "SELECT COUNT(*) FROM provider_credentials WHERE account_id = '$SAFE_ACCOUNT_ID'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query(
            "SELECT COUNT(*) FROM provider_playback_sessions WHERE account_id = '$SAFE_ACCOUNT_ID'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query(
            "SELECT id FROM provider_playback_sessions"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("safe-session", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
        migrated.query(
            "SELECT channel_id FROM channel_playback_references ORDER BY channel_id"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
        }
        migrated.query(
            """
            SELECT base_url, requires_reauthentication
            FROM provider_accounts WHERE id = '$SAFE_ACCOUNT_ID'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("https://media.example.test/emby", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        assertFalse(migrated.hasColumn("channel_playback_references", "fallback_direct_url"))
        assertFalse(migrated.hasColumn("provider_playback_sessions", "fallback_direct_url"))
        runBlocking {
            val remainingAccounts = database.providerDao().getAccounts()
            remainingAccounts.forEach { account ->
                ExtensionId(account.providerId)
                ProviderKind(account.providerKind)
            }
            assertEquals(
                remainingAccounts.map { account -> account.id }.sorted(),
                database.providerDao().observeAccountSummaries().first()
                    .map { summary -> summary.account.id }
                    .sorted(),
            )
        }
        database.close()
        context.getDatabasePath(DATABASE_NAME).parentFile
            ?.listFiles { file -> file.name.startsWith(DATABASE_NAME) }
            .orEmpty()
            .forEach { file ->
                val physicalContents = file.readBytes()
                listOf(
                    USERINFO_SECRET,
                    PASSWORD_SECRET,
                    QUERY_SECRET,
                    FRAGMENT_SECRET,
                    INVALID_URL_SECRET,
                    FALLBACK_SECRET,
                ).forEach { secret ->
                    assertFalse(
                        "${file.name} retained plaintext secret bytes",
                        physicalContents.contains(secret.toByteArray(StandardCharsets.UTF_8)),
                    )
                }
            }
    }

    private fun assertSanitizedAndInvalidated(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        accountId: String,
    ) {
        database.query(
            """
            SELECT base_url, requires_reauthentication
            FROM provider_accounts WHERE id = ?
            """.trimIndent(),
            arrayOf(accountId),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("https://media.example.test/emby", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        assertRowCount(database, "provider_credentials", "account_id", accountId, 0)
        assertRowCount(database, "provider_playback_sessions", "account_id", accountId, 0)
    }

    private fun assertRowCount(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        column: String,
        value: String,
        expected: Int,
    ) {
        database.query(
            "SELECT COUNT(*) FROM `$table` WHERE `$column` = ?",
            arrayOf(value),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        return indices.any { start ->
            start + needle.size <= size &&
                needle.indices.all { offset -> this[start + offset] == needle[offset] }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertPlaylist(
        url: String,
        title: String,
    ) {
        execSQL(
            """
            INSERT INTO playlists (
                title, url, pinned_groups, hidden_groups, source, epg_urls,
                auto_refresh_programmes
            ) VALUES (?, ?, '[]', '[]', 'provider', '[]', 0)
            """.trimIndent(),
            arrayOf(title, url),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertAccount(
        id: String,
        playlistUrl: String,
        baseUrl: String,
        providerId: String = PROVIDER_ID,
        providerKind: String = "test",
    ) {
        execSQL(
            """
            INSERT INTO provider_accounts (
                id, provider_id, provider_kind, base_url, server_id, server_name,
                server_version, user_id, username, playlist_url,
                requires_reauthentication, owner_package_name, owner_service_name,
                owner_certificate_sha256
            ) VALUES (
                ?, ?, ?, ?, ?, 'Server', '1',
                ?, 'viewer', ?, 0, NULL, NULL, NULL
            )
            """.trimIndent(),
            arrayOf(
                id,
                providerId,
                providerKind,
                baseUrl,
                "server-$id",
                "user-$id",
                playlistUrl,
            ),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertCredential(
        accountId: String,
        handle: String,
    ) {
        execSQL(
            """
            INSERT INTO provider_credentials (
                account_id, credential_handle, ciphertext, nonce, key_version
            ) VALUES (?, ?, 'ciphertext', 'nonce', 1)
            """.trimIndent(),
            arrayOf(accountId, handle),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertSession(
        id: String,
        accountId: String,
        fallbackUrl: String,
        providerId: String = PROVIDER_ID,
        itemId: String = "item",
        mediaSourceId: String? = null,
        sourceType: String = "live",
        playSessionId: String? = "play-session",
        liveStreamId: String? = "live-stream",
        createdAtEpochMillis: Long = 1_000L,
    ) {
        execSQL(
            """
            INSERT INTO provider_playback_sessions (
                id, account_id, provider_id, item_id, media_source_id, source_type,
                fallback_direct_url, play_session_id, live_stream_id,
                created_at_epoch_millis
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """.trimIndent(),
            arrayOf<Any?>(
                id,
                accountId,
                providerId,
                itemId,
                mediaSourceId,
                sourceType,
                fallbackUrl,
                playSessionId,
                liveStreamId,
                createdAtEpochMillis,
            ),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertChannelReference(
        channelId: Int,
        accountId: String,
        playlistUrl: String,
        providerId: String = PROVIDER_ID,
        itemId: String = "item-$channelId",
        mediaSourceId: String? = null,
        sourceType: String = "live",
    ) {
        execSQL(
            """
            INSERT INTO streams (
                id, url, `group`, title, cover, playlist_url, license_type, license_key,
                favourite, hidden, seen, relation_id
            ) VALUES (?, 'm3u-provider-dynamic', 'Test', ?, NULL, ?, NULL, NULL, 0, 0, 0, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                channelId,
                "Channel $channelId",
                playlistUrl,
                "channel-$channelId",
            ),
        )
        execSQL(
            """
            INSERT INTO channel_playback_references (
                channel_id, account_id, provider_id, item_id, media_source_id, source_type,
                fallback_direct_url
            ) VALUES (?, ?, ?, ?, ?, ?, NULL)
            """.trimIndent(),
            arrayOf<Any?>(
                channelId,
                accountId,
                providerId,
                itemId,
                mediaSourceId,
                sourceType,
            ),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.hasColumn(
        table: String,
        column: String,
    ): Boolean = query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        var found = false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) {
                found = true
                break
            }
        }
        found
    }

    private companion object {
        const val DATABASE_NAME = "migration-24-25"
        const val USERINFO_ACCOUNT_ID = "userinfo-account"
        const val QUERY_ACCOUNT_ID = "query-account"
        const val FRAGMENT_ACCOUNT_ID = "fragment-account"
        const val INVALID_ACCOUNT_ID = "invalid-account"
        const val INVALID_PROVIDER_ID_ACCOUNT_ID = "invalid-provider-id-account"
        const val INVALID_PROVIDER_KIND_ACCOUNT_ID = "invalid-provider-kind-account"
        const val SAFE_ACCOUNT_ID = "safe-account"
        const val USERINFO_PLAYLIST_URL = "m3u-provider://account/userinfo-account/live"
        const val QUERY_PLAYLIST_URL = "m3u-provider://account/query-account/live"
        const val FRAGMENT_PLAYLIST_URL = "m3u-provider://account/fragment-account/live"
        const val INVALID_PLAYLIST_URL = "m3u-provider://account/invalid-account/live"
        const val INVALID_PROVIDER_ID_PLAYLIST_URL =
            "m3u-provider://account/invalid-provider-id-account/live"
        const val INVALID_PROVIDER_KIND_PLAYLIST_URL =
            "m3u-provider://account/invalid-provider-kind-account/live"
        const val SAFE_PLAYLIST_URL = "m3u-provider://account/safe-account/live"
        const val USERINFO_SECRET = "migration-userinfo-secret"
        const val PASSWORD_SECRET = "migration-password-secret"
        const val QUERY_SECRET = "migration-query-secret"
        const val FRAGMENT_SECRET = "migration-fragment-secret"
        const val INVALID_URL_SECRET = "migration-invalid-url-secret"
        const val FALLBACK_SECRET = "migration-fallback-secret"
        const val PROVIDER_ID = "com.m3u.provider.test"
        const val OTHER_PROVIDER_ID = "com.m3u.other.provider"
        val MULTIBYTE_OVERSIZED_ID = "é".repeat(257)
        val MULTIBYTE_OVERSIZED_SOURCE_TYPE = "界".repeat(43)
    }
}
