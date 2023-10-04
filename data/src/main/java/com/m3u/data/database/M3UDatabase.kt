package com.m3u.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.database.dao.FeedDao
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live

@Database(
    entities = [Live::class, Feed::class],
    version = 3,
    exportSchema = true
)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun liveDao(): LiveDao
    abstract fun feedDao(): FeedDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE lives ADD COLUMN banned INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE posts")
            }
        }
    }
}