package com.m3u.data.database

import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object DatabaseMigrations {
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
}