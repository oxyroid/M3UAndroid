package com.m3u.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.m3u.data.local.dao.FeedDao
import com.m3u.data.local.dao.LiveDao
import com.m3u.data.local.entity.Feed
import com.m3u.data.local.entity.Live

@Database(
    entities = [Live::class, Feed::class],
    version = 1,
    exportSchema = false
)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun liveDao(): LiveDao
    abstract fun feedDao(): FeedDao
}