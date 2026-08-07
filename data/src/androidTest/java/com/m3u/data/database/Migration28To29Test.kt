package com.m3u.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cached descriptions must survive a catalogue refresh.
 *
 * Re-subscribing is the only way to refresh a playlist and it writes the row
 * back with INSERT OR REPLACE, which SQLite performs as a delete followed by
 * an insert. While channel_details carried an ON DELETE CASCADE onto playlists
 * that emptied the whole table on every refresh — and each row costs one
 * network request to earn back.
 */
@RunWith(AndroidJUnit4::class)
class Migration28To29Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = M3UDatabase::class.java,
    )

    @Test
    fun cachedDetailsOutliveAPlaylistRefresh() {
        migrationHelper.createDatabase(DATABASE_NAME, 28).apply {
            insertPlaylist()
            insertDetails(reference = "12345", cast = "Jake Gyllenhaal, Riz Ahmed")
            insertDetails(reference = "67890", cast = "Jean Dujardin")
            close()
        }

        val database = Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                M3UDatabase::class.java,
                DATABASE_NAME,
            )
            .allowMainThreadQueries()
            .addMigrations(DatabaseMigrations.MIGRATION_28_29)
            .build()
        val migrated = database.openHelper.writableDatabase

        assertEquals(2, migrated.countDetails())

        // Exactly what a re-subscription does to the playlist row.
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.insertPlaylist(orReplace = true)

        assertEquals(2, migrated.countDetails())
        migrated.query(
            "SELECT `cast` FROM channel_details WHERE channel_reference = '12345'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("Jake Gyllenhaal, Riz Ahmed", cursor.getString(0))
        }
        database.close()
    }

    private fun SupportSQLiteDatabase.countDetails(): Int =
        query("SELECT COUNT(*) FROM channel_details").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.insertPlaylist(orReplace: Boolean = false) {
        val verb = if (orReplace) "INSERT OR REPLACE" else "INSERT"
        execSQL(
            "$verb INTO playlists (url, title) VALUES (?, ?)",
            arrayOf(PLAYLIST_URL, "Provider"),
        )
    }

    private fun SupportSQLiteDatabase.insertDetails(reference: String, cast: String) {
        execSQL(
            """
            INSERT INTO channel_details
                (playlist_url, channel_reference, `cast`, cast_normalized, fetched_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(PLAYLIST_URL, reference, cast, cast.lowercase(), 1L),
        )
    }

    private companion object {
        const val DATABASE_NAME = "migration-28-29"
        const val PLAYLIST_URL = "http://example.test/playlist.m3u"
    }
}
