package com.m3u.data.database

import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object DatabaseMigrations {
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE streams ADD COLUMN media_kind TEXT NOT NULL DEFAULT 'unknown'"
            )
            db.execSQL(
                "ALTER TABLE streams ADD COLUMN playable INTEGER NOT NULL DEFAULT 1"
            )
            db.execSQL(
                "ALTER TABLE streams ADD COLUMN browsable INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE streams ADD COLUMN subtitle TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE streams ADD COLUMN overview TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE streams ADD COLUMN production_year INTEGER DEFAULT NULL")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_accounts` (
                    `id` TEXT NOT NULL,
                    `provider_id` TEXT NOT NULL,
                    `provider_kind` TEXT NOT NULL,
                    `base_url` TEXT NOT NULL,
                    `server_id` TEXT NOT NULL,
                    `server_name` TEXT NOT NULL,
                    `server_version` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `username` TEXT NOT NULL,
                    `playlist_url` TEXT NOT NULL,
                    `requires_reauthentication` INTEGER NOT NULL DEFAULT 0,
                    `owner_package_name` TEXT,
                    `owner_service_name` TEXT,
                    `owner_certificate_sha256` TEXT,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`playlist_url`) REFERENCES `playlists`(`url`)
                        ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_accounts_playlist_url`
                ON `provider_accounts` (`playlist_url`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_provider_accounts_provider_id_server_id_user_id`
                ON `provider_accounts` (`provider_id`, `server_id`, `user_id`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_credentials` (
                    `account_id` TEXT NOT NULL,
                    `credential_handle` TEXT NOT NULL,
                    `ciphertext` TEXT NOT NULL,
                    `nonce` TEXT NOT NULL,
                    `key_version` INTEGER NOT NULL,
                    PRIMARY KEY(`account_id`),
                    FOREIGN KEY(`account_id`) REFERENCES `provider_accounts`(`id`)
                        ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `channel_playback_references` (
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
                CREATE INDEX IF NOT EXISTS `index_channel_playback_references_account_id`
                ON `channel_playback_references` (`account_id`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_playback_sessions` (
                    `id` TEXT NOT NULL,
                    `account_id` TEXT NOT NULL,
                    `provider_id` TEXT NOT NULL,
                    `item_id` TEXT NOT NULL,
                    `media_source_id` TEXT,
                    `source_type` TEXT NOT NULL,
                    `play_session_id` TEXT,
                    `live_stream_id` TEXT,
                    `created_at_epoch_millis` INTEGER NOT NULL,
                    `play_method` TEXT NOT NULL DEFAULT 'unknown',
                    `position_ticks` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`account_id`) REFERENCES `provider_accounts`(`id`)
                        ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_provider_playback_sessions_account_id`
                ON `provider_playback_sessions` (`account_id`)
                """.trimIndent()
            )
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

}
