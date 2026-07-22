package com.m3u.data.database

import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.extension.security.CredentialVault

internal object DatabaseMigrations {
    fun migration22To23(credentialVault: CredentialVault) = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
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

}
