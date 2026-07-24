package com.m3u.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration23To24Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = M3UDatabase::class.java,
    )

    @Test
    fun migrationAddsExternalOwnerWithoutClaimingExistingBuiltInAccounts() {
        migrationHelper.createDatabase(DATABASE_NAME, 23).apply {
            execSQL(
                """
                INSERT INTO playlists (
                    title, url, pinned_groups, hidden_groups, source, epg_urls,
                    auto_refresh_programmes
                ) VALUES (
                    'Existing provider', 'm3u-provider://account/a/live', '[]', '[]',
                    'provider', '[]', 0
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO provider_accounts (
                    id, provider_id, provider_kind, base_url, server_id, server_name,
                    server_version, user_id, username, playlist_url, requires_reauthentication
                ) VALUES (
                    'a', 'com.m3u.emby-compatible', 'emby', 'https://example.test',
                    'server', 'Server', '1', 'user', 'name',
                    'm3u-provider://account/a/live', 0
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO provider_credentials (
                    account_id, credential_handle, ciphertext, nonce, key_version
                ) VALUES ('a', 'credential-a', 'ciphertext', 'nonce', 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO provider_playback_sessions (
                    id, account_id, provider_id, item_id, media_source_id, source_type,
                    fallback_direct_url, play_session_id, live_stream_id,
                    created_at_epoch_millis
                ) VALUES (
                    'session-a', 'a', 'com.m3u.emby-compatible', 'item-a', NULL,
                    'live', NULL, 'remote-session-a', 'live-stream-a', 1000
                )
                """.trimIndent()
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

        migrated.query(
            """
            SELECT provider_id, owner_package_name, owner_service_name,
                owner_certificate_sha256
            FROM provider_accounts WHERE id = 'a'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("com.m3u.emby-compatible", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
            assertNull(cursor.getString(3))
        }
        migrated.query(
            "SELECT credential_handle FROM provider_credentials WHERE account_id = 'a'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("credential-a", cursor.getString(0))
        }
        migrated.query(
            "SELECT id FROM provider_playback_sessions WHERE account_id = 'a'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("session-a", cursor.getString(0))
        }
        database.close()
    }

    private companion object {
        const val DATABASE_NAME = "migration-23-24"
    }
}
