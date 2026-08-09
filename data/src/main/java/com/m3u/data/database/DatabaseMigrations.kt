package com.m3u.data.database

import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.extension.security.CredentialVault
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object DatabaseMigrations {
    fun migration22To23(credentialVault: CredentialVault) = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.enableSecureDelete()
            db.execSQL(
                "ALTER TABLE provider_accounts ADD COLUMN requires_reauthentication INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_credentials_new` (
                    `account_id` TEXT NOT NULL,
                    `credential_handle` TEXT NOT NULL,
                    `ciphertext` TEXT NOT NULL,
                    `nonce` TEXT NOT NULL,
                    `key_version` INTEGER NOT NULL,
                    PRIMARY KEY(`account_id`),
                    FOREIGN KEY(`account_id`) REFERENCES `provider_accounts`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            val insert = db.compileStatement(
                """
                INSERT OR REPLACE INTO provider_credentials_new (
                    account_id, credential_handle, ciphertext, nonce, key_version
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            )
            db.query("SELECT account_id, access_token FROM provider_credentials").use { cursor ->
                while (cursor.moveToNext()) {
                    val accountId = cursor.getString(0)
                    val accessToken = cursor.getString(1)
                    val encrypted = runCatching {
                        credentialVault.encrypt(accountId, accessToken)
                    }.getOrNull()
                    if (encrypted == null) {
                        db.execSQL(
                            "UPDATE provider_accounts SET requires_reauthentication = 1 WHERE id = ?",
                            arrayOf(accountId),
                        )
                    } else {
                        insert.clearBindings()
                        insert.bindString(1, encrypted.accountId)
                        insert.bindString(2, encrypted.credentialHandle)
                        insert.bindString(3, encrypted.ciphertext)
                        insert.bindString(4, encrypted.nonce)
                        insert.bindLong(5, encrypted.keyVersion.toLong())
                        insert.executeInsert()
                    }
                }
            }
            db.execSQL(
                "UPDATE provider_credentials SET access_token = zeroblob(length(access_token))"
            )
            db.execSQL("DROP TABLE provider_credentials")
            db.execSQL("ALTER TABLE provider_credentials_new RENAME TO provider_credentials")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_playback_sessions` (
                    `id` TEXT NOT NULL,
                    `account_id` TEXT NOT NULL,
                    `provider_id` TEXT NOT NULL,
                    `item_id` TEXT NOT NULL,
                    `media_source_id` TEXT,
                    `source_type` TEXT NOT NULL,
                    `fallback_direct_url` TEXT,
                    `play_session_id` TEXT,
                    `live_stream_id` TEXT,
                    `created_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`account_id`) REFERENCES `provider_accounts`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_provider_playback_sessions_account_id` ON `provider_playback_sessions` (`account_id`)"
            )
            db.execSQL(
                "UPDATE playlists SET source = 'provider' WHERE url IN (SELECT playlist_url FROM provider_accounts)"
            )
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lives ADD COLUMN banned INTEGER NOT NULL DEFAULT 0")
        }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE posts")
        }
    }

    @RenameColumn(
        tableName = "lives",
        fromColumnName = "feedUrl",
        toColumnName = "playlistUrl"
    )
    @RenameTable(fromTableName = "feeds", toTableName = "playlists")
    @RenameTable(fromTableName = "lives", toTableName = "streams")
    class AutoMigration3To4 : AutoMigrationSpec

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN pinned_groups TEXT NOT NULL DEFAULT '[]'")
        }
    }

    @RenameColumn(
        tableName = "streams",
        fromColumnName = "banned",
        toColumnName = "hidden"
    )
    class AutoMigration8To9 : AutoMigrationSpec

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN hidden_groups TEXT NOT NULL DEFAULT '[]'")
        }
    }


    @DeleteColumn(
        tableName = "playlists",
        columnName = "epg_url"
    )
    class AutoMigrate14To16 : AutoMigrationSpec

    @DeleteColumn.Entries(
        DeleteColumn(tableName = "programmes", columnName = "new"),
        DeleteColumn(tableName = "programmes", columnName = "live"),
        DeleteColumn(tableName = "programmes", columnName = "previous_start")
    )
    class AutoMigrate18To19: AutoMigrationSpec

    @RenameColumn.Entries(
        RenameColumn(
            tableName = "streams",
            fromColumnName = "channel_id",
            toColumnName = "relation_id"
        ),
        RenameColumn(
            tableName = "programmes",
            fromColumnName = "channel_id",
            toColumnName = "relation_id"
        ),
        RenameColumn(
            tableName = "streams",
            fromColumnName = "playlistUrl",
            toColumnName = "playlist_url"
        )
    )
    class AutoMigrate19To20: AutoMigrationSpec

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.enableSecureDelete()
            val accounts = buildList {
                db.query(
                    """
                    SELECT id, playlist_url, base_url, provider_id, provider_kind
                    FROM provider_accounts
                    """.trimIndent()
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        add(
                            ProviderBaseUrlMigrationRow(
                                accountId = cursor.getString(0),
                                playlistUrl = cursor.getString(1),
                                baseUrl = cursor.getString(2),
                                providerId = cursor.getString(3),
                                providerKind = cursor.getString(4),
                            )
                        )
                    }
                }
            }
            accounts.forEach { account ->
                val hasValidProviderContract = runCatching {
                    ExtensionId(account.providerId)
                    ProviderKind(account.providerKind)
                }.isSuccess
                val parsed = account.baseUrl.toHttpUrlOrNull()
                if (!hasValidProviderContract || parsed == null) {
                    db.deleteProviderAccountData(account)
                } else if (
                    parsed.username.isNotEmpty() ||
                    parsed.password.isNotEmpty() ||
                    parsed.query != null ||
                    parsed.fragment != null
                ) {
                    val sanitized = parsed.newBuilder()
                        .username("")
                        .password("")
                        .query(null)
                        .fragment(null)
                        .build()
                        .toString()
                    db.execSQL(
                        "DELETE FROM provider_credentials WHERE account_id = ?",
                        arrayOf(account.accountId),
                    )
                    db.execSQL(
                        "DELETE FROM provider_playback_sessions WHERE account_id = ?",
                        arrayOf(account.accountId),
                    )
                    db.execSQL(
                        """
                        UPDATE provider_accounts
                        SET base_url = ?, requires_reauthentication = 1
                        WHERE id = ?
                        """.trimIndent(),
                        arrayOf(sanitized, account.accountId),
                    )
                }
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `channel_playback_references_new` (
                    `channel_id` INTEGER NOT NULL,
                    `account_id` TEXT NOT NULL,
                    `provider_id` TEXT NOT NULL,
                    `item_id` TEXT NOT NULL,
                    `media_source_id` TEXT,
                    `source_type` TEXT NOT NULL,
                    PRIMARY KEY(`channel_id`),
                    FOREIGN KEY(`channel_id`) REFERENCES `streams`(`id`)
                        ON UPDATE CASCADE ON DELETE CASCADE,
                    FOREIGN KEY(`account_id`) REFERENCES `provider_accounts`(`id`)
                        ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO channel_playback_references_new (
                    channel_id, account_id, provider_id, item_id, media_source_id, source_type
                )
                SELECT reference.channel_id, reference.account_id, reference.provider_id,
                    reference.item_id, reference.media_source_id, reference.source_type
                FROM channel_playback_references AS reference
                INNER JOIN provider_accounts AS account
                    ON account.id = reference.account_id
                    AND account.provider_id = reference.provider_id
                WHERE LENGTH(CAST(reference.provider_id AS BLOB)) BETWEEN 1 AND 128
                AND reference.provider_id NOT GLOB '*[^a-z0-9._-]*'
                AND SUBSTR(reference.provider_id, 1, 1) GLOB '[a-z0-9]'
                AND SUBSTR(reference.provider_id, -1, 1) GLOB '[a-z0-9]'
                AND reference.provider_id NOT GLOB '*[._-][._-]*'
                AND NULLIF(TRIM(reference.item_id), '') IS NOT NULL
                AND LENGTH(CAST(reference.item_id AS BLOB)) <= 512
                AND (
                    reference.media_source_id IS NULL
                    OR (
                        NULLIF(TRIM(reference.media_source_id), '') IS NOT NULL
                        AND LENGTH(CAST(reference.media_source_id AS BLOB)) <= 512
                    )
                )
                AND NULLIF(TRIM(reference.source_type), '') IS NOT NULL
                AND LENGTH(CAST(reference.source_type AS BLOB)) <= 128
                """.trimIndent()
            )
            db.execSQL("DROP TABLE channel_playback_references")
            db.execSQL(
                "ALTER TABLE channel_playback_references_new RENAME TO channel_playback_references"
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_channel_playback_references_account_id`
                ON `channel_playback_references` (`account_id`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_playback_sessions_new` (
                    `id` TEXT NOT NULL,
                    `account_id` TEXT NOT NULL,
                    `provider_id` TEXT NOT NULL,
                    `item_id` TEXT NOT NULL,
                    `media_source_id` TEXT,
                    `source_type` TEXT NOT NULL,
                    `play_session_id` TEXT,
                    `live_stream_id` TEXT,
                    `created_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`account_id`) REFERENCES `provider_accounts`(`id`)
                        ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO provider_playback_sessions_new (
                    id, account_id, provider_id, item_id, media_source_id, source_type,
                    play_session_id, live_stream_id, created_at_epoch_millis
                )
                SELECT session.id, session.account_id, session.provider_id, session.item_id,
                    session.media_source_id, session.source_type, session.play_session_id,
                    session.live_stream_id, session.created_at_epoch_millis
                FROM provider_playback_sessions AS session
                INNER JOIN provider_accounts AS account
                    ON account.id = session.account_id
                    AND account.provider_id = session.provider_id
                WHERE NULLIF(TRIM(session.id), '') IS NOT NULL
                AND LENGTH(CAST(session.id AS BLOB)) <= 512
                AND session.created_at_epoch_millis >= 0
                AND LENGTH(CAST(session.provider_id AS BLOB)) BETWEEN 1 AND 128
                AND session.provider_id NOT GLOB '*[^a-z0-9._-]*'
                AND SUBSTR(session.provider_id, 1, 1) GLOB '[a-z0-9]'
                AND SUBSTR(session.provider_id, -1, 1) GLOB '[a-z0-9]'
                AND session.provider_id NOT GLOB '*[._-][._-]*'
                AND NULLIF(TRIM(session.item_id), '') IS NOT NULL
                AND LENGTH(CAST(session.item_id AS BLOB)) <= 512
                AND (
                    session.media_source_id IS NULL
                    OR (
                        NULLIF(TRIM(session.media_source_id), '') IS NOT NULL
                        AND LENGTH(CAST(session.media_source_id AS BLOB)) <= 512
                    )
                )
                AND NULLIF(TRIM(session.source_type), '') IS NOT NULL
                AND LENGTH(CAST(session.source_type AS BLOB)) <= 128
                AND (
                    NULLIF(TRIM(session.play_session_id), '') IS NOT NULL
                    OR NULLIF(TRIM(session.live_stream_id), '') IS NOT NULL
                )
                AND (
                    session.play_session_id IS NULL
                    OR (
                        NULLIF(TRIM(session.play_session_id), '') IS NOT NULL
                        AND LENGTH(CAST(session.play_session_id AS BLOB)) <= 512
                    )
                )
                AND (
                    session.live_stream_id IS NULL
                    OR (
                        NULLIF(TRIM(session.live_stream_id), '') IS NOT NULL
                        AND LENGTH(CAST(session.live_stream_id AS BLOB)) <= 512
                    )
                )
                """.trimIndent()
            )
            db.execSQL("DROP TABLE provider_playback_sessions")
            db.execSQL(
                "ALTER TABLE provider_playback_sessions_new RENAME TO provider_playback_sessions"
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_provider_playback_sessions_account_id`
                ON `provider_playback_sessions` (`account_id`)
                """.trimIndent()
            )
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `channel_metadata_bases` (
                    `playlist_url` TEXT NOT NULL,
                    `channel_reference` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    PRIMARY KEY(`playlist_url`, `channel_reference`),
                    FOREIGN KEY(`playlist_url`) REFERENCES `playlists`(`url`)
                        ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_channel_metadata_bases_playlist_url`
                ON `channel_metadata_bases` (`playlist_url`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `extension_channel_metadata_overlays` (
                    `playlist_url` TEXT NOT NULL,
                    `channel_reference` TEXT NOT NULL,
                    `extension_id` TEXT NOT NULL,
                    `title` TEXT,
                    `category` TEXT,
                    PRIMARY KEY(`playlist_url`, `channel_reference`, `extension_id`),
                    FOREIGN KEY(`playlist_url`, `channel_reference`)
                        REFERENCES `channel_metadata_bases`(
                            `playlist_url`, `channel_reference`
                        )
                        ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                    `index_extension_channel_metadata_overlays_playlist_url_channel_reference`
                ON `extension_channel_metadata_overlays` (
                    `playlist_url`, `channel_reference`
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                    `index_extension_channel_metadata_overlays_extension_id`
                ON `extension_channel_metadata_overlays` (`extension_id`)
                """.trimIndent()
            )
            /*
             * v25 stored only the already-effective stream title/category. There is no owner
             * provenance to reconstruct a pre-enrichment value, so that effective value is the
             * only safe initial source base. All v26 imports keep source bases separate.
             */
            db.execSQL(
                """
                INSERT INTO channel_metadata_bases (
                    playlist_url, channel_reference, title, category
                )
                SELECT stream.playlist_url, stream.relation_id, stream.title, stream.`group`
                FROM streams AS stream
                INNER JOIN (
                    SELECT playlist_url, relation_id, MIN(id) AS first_stream_id
                    FROM streams
                    WHERE NULLIF(TRIM(relation_id), '') IS NOT NULL
                    GROUP BY playlist_url, relation_id
                ) AS first_stream
                    ON first_stream.first_stream_id = stream.id
                INNER JOIN playlists AS playlist
                    ON playlist.url = stream.playlist_url
                """.trimIndent()
            )
        }
    }

    private data class ProviderBaseUrlMigrationRow(
        val accountId: String,
        val playlistUrl: String,
        val baseUrl: String,
        val providerId: String,
        val providerKind: String,
    )

    private fun SupportSQLiteDatabase.deleteProviderAccountData(
        account: ProviderBaseUrlMigrationRow,
    ) {
        execSQL(
            "DELETE FROM provider_credentials WHERE account_id = ?",
            arrayOf(account.accountId),
        )
        execSQL(
            "DELETE FROM channel_playback_references WHERE account_id = ?",
            arrayOf(account.accountId),
        )
        execSQL(
            "DELETE FROM streams WHERE playlist_url = ?",
            arrayOf(account.playlistUrl),
        )
        execSQL(
            "DELETE FROM provider_playback_sessions WHERE account_id = ?",
            arrayOf(account.accountId),
        )
        execSQL(
            "DELETE FROM provider_accounts WHERE id = ?",
            arrayOf(account.accountId),
        )
        execSQL(
            "DELETE FROM playlists WHERE url = ?",
            arrayOf(account.playlistUrl),
        )
    }

    private fun SupportSQLiteDatabase.enableSecureDelete() {
        query("PRAGMA secure_delete = ON").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 1) {
                "SQLite secure_delete could not be enabled"
            }
        }
    }

}
