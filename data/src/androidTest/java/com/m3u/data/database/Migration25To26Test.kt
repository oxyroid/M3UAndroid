package com.m3u.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration25To26Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = M3UDatabase::class.java,
    )

    @Test
    fun migrationUsesCurrentEffectiveMetadataAsTheOnlyRecoverableSourceBase() {
        migrationHelper.createDatabase(DATABASE_NAME, 25).apply {
            execSQL(
                """
                INSERT INTO playlists (
                    title, url, pinned_groups, hidden_groups, source, epg_urls,
                    auto_refresh_programmes
                ) VALUES (
                    'Playlist', '$PLAYLIST_URL', '[]', '[]', 'm3u', '[]', 0
                )
                """.trimIndent()
            )
            insertStream(
                id = 1,
                reference = CHANNEL_REFERENCE,
                title = "Current effective title",
                category = "Current effective category",
            )
            insertStream(
                id = 2,
                reference = CHANNEL_REFERENCE,
                title = "Duplicate title",
                category = "Duplicate category",
            )
            insertStream(
                id = 3,
                reference = "",
                title = "Unstable title",
                category = "Unstable category",
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, M3UDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .addMigrations(DatabaseMigrations.MIGRATION_25_26)
            .build()
        val migrated = database.openHelper.writableDatabase

        migrated.query(
            """
            SELECT channel_reference, title, category
            FROM channel_metadata_bases
            WHERE playlist_url = '$PLAYLIST_URL'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(CHANNEL_REFERENCE, cursor.getString(0))
            // v25 has no contribution owner history, so migration cannot infer an older value.
            assertEquals("Current effective title", cursor.getString(1))
            assertEquals("Current effective category", cursor.getString(2))
            assertFalse(cursor.moveToNext())
        }
        migrated.query(
            "SELECT COUNT(*) FROM extension_channel_metadata_overlays"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        migrated.execSQL("DELETE FROM playlists WHERE url = '$PLAYLIST_URL'")
        migrated.query("SELECT COUNT(*) FROM channel_metadata_bases").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    private fun SupportSQLiteDatabase.insertStream(
        id: Int,
        reference: String,
        title: String,
        category: String,
    ) {
        execSQL(
            """
            INSERT INTO streams (
                url, `group`, title, playlist_url, id, favourite, hidden, seen, relation_id
            ) VALUES (?, ?, ?, ?, ?, 0, 0, 0, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "https://example.test/$id",
                category,
                title,
                PLAYLIST_URL,
                id,
                reference,
            ),
        )
    }

    private companion object {
        const val DATABASE_NAME = "migration-25-26"
        const val PLAYLIST_URL = "https://example.test/playlist.m3u"
        const val CHANNEL_REFERENCE = "channel-1"
    }
}
