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
class Migration21To22Test {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = M3UDatabase::class.java,
    )

    @Test
    fun migrationPreservesVersion21DataAndCreatesExactVersion22Schema() {
        migrationHelper.createDatabase(DATABASE_NAME, 21).apply {
            insertPlaylist(
                title = "M3U favorites",
                url = M3U_PLAYLIST_URL,
                source = "m3u",
                pinnedCategories = """["News"]""",
                hiddenCategories = """["Hidden"]""",
                userAgent = "M3UAndroid migration test",
                epgUrls = """["$EPG_PLAYLIST_URL"]""",
                autoRefreshProgrammes = 1,
            )
            insertPlaylist(
                title = "Xtream library",
                url = XTREAM_PLAYLIST_URL,
                source = "xtream",
                pinnedCategories = """["Movies"]""",
                hiddenCategories = "[]",
                userAgent = null,
                epgUrls = "[]",
                autoRefreshProgrammes = 0,
            )
            insertPlaylist(
                title = "Programme guide",
                url = EPG_PLAYLIST_URL,
                source = "epg",
                pinnedCategories = "[]",
                hiddenCategories = "[]",
                userAgent = null,
                epgUrls = "[]",
                autoRefreshProgrammes = 0,
            )
            insertStream(
                id = M3U_CHANNEL_ID,
                playlistUrl = M3U_PLAYLIST_URL,
                url = "https://media.example.test/live.m3u8",
                category = "News",
                title = "M3U channel",
                cover = "https://media.example.test/m3u.png",
                licenseType = "clearkey",
                licenseKey = "key",
                favourite = 1,
                hidden = 0,
                seen = 1234L,
                relationId = M3U_CHANNEL_REFERENCE,
            )
            insertStream(
                id = XTREAM_CHANNEL_ID,
                playlistUrl = XTREAM_PLAYLIST_URL,
                url = "https://media.example.test/movie.ts",
                category = "Movies",
                title = "Xtream channel",
                cover = null,
                licenseType = null,
                licenseKey = null,
                favourite = 0,
                hidden = 1,
                seen = 5678L,
                relationId = XTREAM_CHANNEL_REFERENCE,
            )
            insertStream(
                id = CHANNEL_WITHOUT_REFERENCE_ID,
                playlistUrl = M3U_PLAYLIST_URL,
                url = "https://media.example.test/no-reference.m3u8",
                category = "Other",
                title = "Channel without reference",
                cover = null,
                licenseType = null,
                licenseKey = null,
                favourite = 0,
                hidden = 0,
                seen = 0L,
                relationId = null,
            )
            execSQL(
                """
                INSERT INTO programmes (
                    relation_id, epg_url, start, `end`, title, description,
                    icon, categories, id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    M3U_CHANNEL_REFERENCE,
                    EPG_PLAYLIST_URL,
                    1_000L,
                    2_000L,
                    "Morning news",
                    "Version 21 programme",
                    "https://media.example.test/programme.png",
                    """["News","Live"]""",
                    PROGRAMME_ID,
                ),
            )
            execSQL(
                """
                INSERT INTO episodes (
                    title, series_id, season, number, url, id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "Pilot",
                    XTREAM_CHANNEL_ID,
                    "1",
                    1,
                    "https://media.example.test/episode.mp4",
                    EPISODE_ID,
                ),
            )
            execSQL(
                "INSERT INTO color_pack (argb, dark, name) VALUES (?, ?, ?)",
                arrayOf<Any?>(COLOR_ARGB, 1, "Migration theme"),
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, M3UDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .addMigrations(DatabaseMigrations.MIGRATION_21_22)
            .build()
        val migrated = database.openHelper.writableDatabase

        assertEquals(22L, migrated.longValue("PRAGMA user_version"))
        assertEquals(3L, migrated.longValue("SELECT COUNT(*) FROM playlists"))
        assertPlaylistPreserved(migrated)
        assertStreamsPreservedWithFinalDefaults(migrated)
        assertProgrammeEpisodeAndColorPreserved(migrated)
        assertFinalExtensionTables(migrated)
        assertMetadataBasesBackfilled(migrated)
        migrated.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }

        database.close()
    }

    private fun SupportSQLiteDatabase.insertPlaylist(
        title: String,
        url: String,
        source: String,
        pinnedCategories: String,
        hiddenCategories: String,
        userAgent: String?,
        epgUrls: String,
        autoRefreshProgrammes: Int,
    ) {
        execSQL(
            """
            INSERT INTO playlists (
                title, url, pinned_groups, hidden_groups, source, user_agent,
                epg_urls, auto_refresh_programmes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                title,
                url,
                pinnedCategories,
                hiddenCategories,
                source,
                userAgent,
                epgUrls,
                autoRefreshProgrammes,
            ),
        )
    }

    private fun SupportSQLiteDatabase.insertStream(
        id: Int,
        playlistUrl: String,
        url: String,
        category: String,
        title: String,
        cover: String?,
        licenseType: String?,
        licenseKey: String?,
        favourite: Int,
        hidden: Int,
        seen: Long,
        relationId: String?,
    ) {
        execSQL(
            """
            INSERT INTO streams (
                url, `group`, title, cover, playlist_url, license_type, license_key,
                id, favourite, hidden, seen, relation_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                url,
                category,
                title,
                cover,
                playlistUrl,
                licenseType,
                licenseKey,
                id,
                favourite,
                hidden,
                seen,
                relationId,
            ),
        )
    }

    private fun assertPlaylistPreserved(db: SupportSQLiteDatabase) {
        db.query(
            """
            SELECT title, pinned_groups, hidden_groups, source, user_agent,
                epg_urls, auto_refresh_programmes
            FROM playlists
            WHERE url = ?
            """.trimIndent(),
            arrayOf(M3U_PLAYLIST_URL),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("M3U favorites", cursor.getString(0))
            assertEquals("""["News"]""", cursor.getString(1))
            assertEquals("""["Hidden"]""", cursor.getString(2))
            assertEquals("m3u", cursor.getString(3))
            assertEquals("M3UAndroid migration test", cursor.getString(4))
            assertEquals("""["$EPG_PLAYLIST_URL"]""", cursor.getString(5))
            assertEquals(1, cursor.getInt(6))
        }
        db.query(
            "SELECT source FROM playlists WHERE url = ?",
            arrayOf(XTREAM_PLAYLIST_URL),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("xtream", cursor.getString(0))
        }
        db.query(
            "SELECT source FROM playlists WHERE url = ?",
            arrayOf(EPG_PLAYLIST_URL),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("epg", cursor.getString(0))
        }
    }

    private fun assertStreamsPreservedWithFinalDefaults(db: SupportSQLiteDatabase) {
        assertEquals(3L, db.longValue("SELECT COUNT(*) FROM streams"))
        db.query(
            """
            SELECT url, `group`, title, cover, playlist_url, license_type, license_key,
                favourite, hidden, seen, relation_id, media_kind, playable, browsable,
                subtitle, overview, production_year
            FROM streams
            WHERE id = ?
            """.trimIndent(),
            arrayOf(M3U_CHANNEL_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("https://media.example.test/live.m3u8", cursor.getString(0))
            assertEquals("News", cursor.getString(1))
            assertEquals("M3U channel", cursor.getString(2))
            assertEquals("https://media.example.test/m3u.png", cursor.getString(3))
            assertEquals(M3U_PLAYLIST_URL, cursor.getString(4))
            assertEquals("clearkey", cursor.getString(5))
            assertEquals("key", cursor.getString(6))
            assertEquals(1, cursor.getInt(7))
            assertEquals(0, cursor.getInt(8))
            assertEquals(1234L, cursor.getLong(9))
            assertEquals(M3U_CHANNEL_REFERENCE, cursor.getString(10))
            assertEquals("unknown", cursor.getString(11))
            assertEquals(1, cursor.getInt(12))
            assertEquals(0, cursor.getInt(13))
            assertTrue(cursor.isNull(14))
            assertTrue(cursor.isNull(15))
            assertTrue(cursor.isNull(16))
        }
        db.query(
            "SELECT title, hidden, seen, media_kind, playable, browsable FROM streams WHERE id = ?",
            arrayOf(XTREAM_CHANNEL_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Xtream channel", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(5678L, cursor.getLong(2))
            assertEquals("unknown", cursor.getString(3))
            assertEquals(1, cursor.getInt(4))
            assertEquals(0, cursor.getInt(5))
        }
    }

    private fun assertProgrammeEpisodeAndColorPreserved(db: SupportSQLiteDatabase) {
        db.query(
            """
            SELECT relation_id, epg_url, start, `end`, title, description, icon, categories
            FROM programmes WHERE id = ?
            """.trimIndent(),
            arrayOf(PROGRAMME_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(M3U_CHANNEL_REFERENCE, cursor.getString(0))
            assertEquals(EPG_PLAYLIST_URL, cursor.getString(1))
            assertEquals(1_000L, cursor.getLong(2))
            assertEquals(2_000L, cursor.getLong(3))
            assertEquals("Morning news", cursor.getString(4))
            assertEquals("Version 21 programme", cursor.getString(5))
            assertEquals("https://media.example.test/programme.png", cursor.getString(6))
            assertEquals("""["News","Live"]""", cursor.getString(7))
        }
        db.query(
            "SELECT title, series_id, season, number, url FROM episodes WHERE id = ?",
            arrayOf(EPISODE_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Pilot", cursor.getString(0))
            assertEquals(XTREAM_CHANNEL_ID, cursor.getInt(1))
            assertEquals("1", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("https://media.example.test/episode.mp4", cursor.getString(4))
        }
        db.query(
            "SELECT name FROM color_pack WHERE argb = ? AND dark = 1",
            arrayOf(COLOR_ARGB),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Migration theme", cursor.getString(0))
        }
    }

    private fun assertFinalExtensionTables(db: SupportSQLiteDatabase) {
        val expectedTables = setOf(
            "provider_accounts",
            "provider_credentials",
            "channel_playback_references",
            "provider_playback_sessions",
            "channel_metadata_bases",
            "extension_channel_metadata_overlays",
        )
        expectedTables.forEach { table ->
            assertTrue("$table was not created", db.hasTable(table))
        }
        expectedTables
            .minus("channel_metadata_bases")
            .forEach { table ->
                assertEquals(0L, db.longValue("SELECT COUNT(*) FROM `$table`"))
            }
        assertEquals(
            setOf("play_method", "position_ticks"),
            db.columns("provider_playback_sessions")
                .intersect(setOf("play_method", "position_ticks")),
        )
        val expectedIndexes = setOf(
            "index_provider_accounts_playlist_url",
            "index_provider_accounts_provider_id_server_id_user_id",
            "index_channel_playback_references_account_id",
            "index_provider_playback_sessions_account_id",
            "index_channel_metadata_bases_playlist_url",
            "index_extension_channel_metadata_overlays_playlist_url_channel_reference",
            "index_extension_channel_metadata_overlays_extension_id",
        )
        assertTrue(db.indexes().containsAll(expectedIndexes))
    }

    private fun assertMetadataBasesBackfilled(db: SupportSQLiteDatabase) {
        assertEquals(2L, db.longValue("SELECT COUNT(*) FROM channel_metadata_bases"))
        db.query(
            """
            SELECT title, category
            FROM channel_metadata_bases
            WHERE playlist_url = ? AND channel_reference = ?
            """.trimIndent(),
            arrayOf(M3U_PLAYLIST_URL, M3U_CHANNEL_REFERENCE),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("M3U channel", cursor.getString(0))
            assertEquals("News", cursor.getString(1))
        }
        db.query(
            """
            SELECT title, category
            FROM channel_metadata_bases
            WHERE playlist_url = ? AND channel_reference = ?
            """.trimIndent(),
            arrayOf(XTREAM_PLAYLIST_URL, XTREAM_CHANNEL_REFERENCE),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Xtream channel", cursor.getString(0))
            assertEquals("Movies", cursor.getString(1))
        }
        assertEquals(
            0L,
            db.longValue(
                """
                SELECT COUNT(*)
                FROM channel_metadata_bases
                WHERE playlist_url = '$M3U_PLAYLIST_URL'
                    AND channel_reference = ''
                """.trimIndent()
            ),
        )
    }

    private fun SupportSQLiteDatabase.longValue(sql: String): Long = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean = query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(tableName),
    ).use { cursor -> cursor.moveToFirst() }

    private fun SupportSQLiteDatabase.columns(tableName: String): Set<String> =
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }

    private fun SupportSQLiteDatabase.indexes(): Set<String> = query(
        "SELECT name FROM sqlite_master WHERE type = 'index' AND name NOT LIKE 'sqlite_%'"
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) {
                add(cursor.getString(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-21-22"
        const val M3U_PLAYLIST_URL = "https://example.test/list.m3u"
        const val XTREAM_PLAYLIST_URL = "xtream://example.test/live"
        const val EPG_PLAYLIST_URL = "https://example.test/guide.xml"
        const val M3U_CHANNEL_ID = 101
        const val XTREAM_CHANNEL_ID = 102
        const val CHANNEL_WITHOUT_REFERENCE_ID = 103
        const val M3U_CHANNEL_REFERENCE = "m3u-channel"
        const val XTREAM_CHANNEL_REFERENCE = "xtream-channel"
        const val PROGRAMME_ID = 201
        const val EPISODE_ID = 301
        const val COLOR_ARGB = 0x112233
    }
}
