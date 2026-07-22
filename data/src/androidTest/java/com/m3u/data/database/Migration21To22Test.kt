package com.m3u.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration21To22Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = M3UDatabase::class.java,
    )

    @Test
    fun migrationCreatesProviderTablesAndPreservesExistingPlaylists() {
        migrationHelper.createDatabase(DATABASE_NAME, 21).apply {
            execSQL(
                """
                INSERT INTO playlists (
                    title,
                    url,
                    pinned_groups,
                    hidden_groups,
                    source,
                    user_agent,
                    epg_urls,
                    auto_refresh_programmes
                ) VALUES ('Existing', 'https://example.test/list.m3u', '[]', '[]', '0', NULL, '[]', 0)
                """.trimIndent()
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, M3UDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .build()
        val migrated = database.openHelper.writableDatabase

        assertTrue(migrated.hasTable("provider_accounts"))
        assertTrue(migrated.hasTable("provider_credentials"))
        assertTrue(migrated.hasTable("channel_playback_references"))
        migrated.query(
            "SELECT title FROM playlists WHERE url = ?",
            arrayOf("https://example.test/list.m3u"),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing", cursor.getString(0))
        }

        database.close()
    }

    private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean = query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(tableName),
    ).use { cursor -> cursor.moveToFirst() }

    private companion object {
        const val DATABASE_NAME = "migration-21-22"
    }
}
