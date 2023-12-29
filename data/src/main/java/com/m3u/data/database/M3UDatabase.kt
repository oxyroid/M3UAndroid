package com.m3u.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.entity.Playlist
import com.m3u.data.database.entity.Stream

@Database(
    entities = [Stream::class, Playlist::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
            spec = M3UDatabase.Companion.AutoMigration3To4::class
        ),
        AutoMigration(from = 4, to = 5)
    ]
)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun streamDao(): StreamDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
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
    }
}
