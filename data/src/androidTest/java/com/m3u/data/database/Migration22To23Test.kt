package com.m3u.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.extension.security.CredentialVault
import com.m3u.extension.api.security.CredentialHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration22To23Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = M3UDatabase::class.java,
    )

    @Test
    fun migrationEncryptsCredentialsGeneralizesProvidersAndCreatesSessionTable() {
        migrationHelper.createDatabase(DATABASE_NAME, 22).apply {
            execSQL(
                "INSERT INTO playlists (title, url, pinned_groups, hidden_groups, source, epg_urls, auto_refresh_programmes) " +
                    "VALUES ('Emby', 'm3u-provider://account/a/live', '[]', '[]', 'emby', '[]', 0)"
            )
            execSQL(
                "INSERT INTO provider_accounts " +
                    "(id, provider_id, provider_kind, base_url, server_id, server_name, server_version, user_id, username, playlist_url) " +
                    "VALUES ('a', 'com.example', 'emby', 'https://example.test', 's', 'Server', '1', 'u', 'user', 'm3u-provider://account/a/live')"
            )
            execSQL("INSERT INTO provider_credentials (account_id, access_token) VALUES ('a', 'plain-token')")
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, M3UDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .addMigrations(DatabaseMigrations.migration22To23(TestCredentialVault))
            .build()
        val migrated = database.openHelper.writableDatabase

        assertTrue(migrated.hasTable("provider_playback_sessions"))
        migrated.query("SELECT source FROM playlists WHERE url = 'm3u-provider://account/a/live'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("provider", cursor.getString(0))
        }
        migrated.query("SELECT credential_handle, ciphertext, nonce, key_version FROM provider_credentials").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("handle-a", cursor.getString(0))
            assertEquals("encrypted-plain-token", cursor.getString(1))
            assertEquals("nonce", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
        }
        migrated.query("PRAGMA table_info(provider_credentials)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
            assertFalse("access_token" in columns)
        }
        database.close()
    }

    private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean = query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(tableName),
    ).use { cursor -> cursor.moveToFirst() }

    private data object TestCredentialVault : CredentialVault {
        override fun encrypt(accountId: String, secret: String, credentialHandle: String?) =
            ProviderCredentialEntity(
                accountId = accountId,
                credentialHandle = credentialHandle ?: "handle-$accountId",
                ciphertext = "encrypted-$secret",
                nonce = "nonce",
                keyVersion = 1,
            )

        override fun decrypt(credential: ProviderCredentialEntity): String? = null

        override fun stage(secret: String): CredentialHandle = CredentialHandle("transient")

        override fun consume(handle: CredentialHandle): String? = null
    }

    private companion object {
        const val DATABASE_NAME = "migration-22-23"
    }
}
