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
 * The folded title column is only useful if the rows already on disk get filled
 * in — a user who has been running the app for months never re-imports their
 * catalogue, so a migration that only adds the column would leave search broken
 * for exactly the people who have the most channels.
 */
@RunWith(AndroidJUnit4::class)
class Migration26To27Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = M3UDatabase::class.java,
    )

    @Test
    fun migrationFoldsExistingTitles() {
        migrationHelper.createDatabase(DATABASE_NAME, 26).apply {
            insertPlaylist(PLAYLIST_URL, "Provider")
            TITLES.forEachIndexed { index, title ->
                insertChannel(id = index + 1, title = title)
            }
            close()
        }

        val database = Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                M3UDatabase::class.java,
                DATABASE_NAME,
            )
            .allowMainThreadQueries()
            .addMigrations(DatabaseMigrations.MIGRATION_26_27)
            .build()
        val migrated = database.openHelper.writableDatabase

        assertEquals(
            listOf(
                "le prenom",
                "amelie",
                "a bout de souffle",
                "spider-man: no way home",
                "千と千尋の神隠し",
            ),
            migrated.readColumn("title_normalized"),
        )
        // The displayed title is untouched — only the search copy is folded.
        assertEquals(TITLES, migrated.readColumn("title"))
        database.close()
    }

    @Test
    fun migrationCoversRowsBeyondASingleBatch() {
        // The backfill walks the table in keyed batches; a catalogue larger than
        // one batch must come out entirely folded, not just its first page.
        val count = 1_200
        migrationHelper.createDatabase(DATABASE_NAME, 26).apply {
            insertPlaylist(PLAYLIST_URL, "Provider")
            repeat(count) { index -> insertChannel(id = index + 1, title = "Épisode $index") }
            close()
        }

        val database = Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                M3UDatabase::class.java,
                DATABASE_NAME,
            )
            .allowMainThreadQueries()
            .addMigrations(DatabaseMigrations.MIGRATION_26_27)
            .build()
        val migrated = database.openHelper.writableDatabase

        migrated.query(
            "SELECT COUNT(*) FROM streams WHERE title_normalized LIKE 'episode %'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(count, cursor.getInt(0))
        }
        database.close()
    }

    private fun SupportSQLiteDatabase.readColumn(column: String): List<String> = buildList {
        query("SELECT $column FROM streams ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun SupportSQLiteDatabase.insertPlaylist(url: String, title: String) {
        execSQL(
            "INSERT INTO playlists (url, title) VALUES (?, ?)",
            arrayOf(url, title),
        )
    }

    private fun SupportSQLiteDatabase.insertChannel(id: Int, title: String) {
        execSQL(
            """
            INSERT INTO streams (id, url, `group`, title, playlist_url, favourite, hidden, seen)
            VALUES (?, ?, ?, ?, ?, 0, 0, 0)
            """.trimIndent(),
            arrayOf<Any>(id, "http://example.test/$id.mkv", "Films", title, PLAYLIST_URL),
        )
    }

    private companion object {
        const val DATABASE_NAME = "migration-26-27"
        const val PLAYLIST_URL = "http://example.test/playlist.m3u"
        val TITLES = listOf(
            "Le Prénom",
            "Amélie",
            "À bout de souffle",
            "Spider-Man: No Way Home",
            "千と千尋の神隠し",
        )
    }
}
