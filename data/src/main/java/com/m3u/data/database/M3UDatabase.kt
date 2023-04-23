package com.m3u.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.database.dao.FeedDao
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.dao.PostDao
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import com.m3u.data.database.entity.Post

@Database(
    entities = [Live::class, Feed::class, Post::class],
    version = 2,
    exportSchema = true
)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun liveDao(): LiveDao
    abstract fun feedDao(): FeedDao
    abstract fun postDao(): PostDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE lives ADD COLUMN banned INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}