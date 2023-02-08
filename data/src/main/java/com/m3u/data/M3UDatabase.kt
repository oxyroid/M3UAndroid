package com.m3u.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.m3u.data.dao.FeedDao
import com.m3u.data.dao.LiveDao
import com.m3u.data.entity.Feed
import com.m3u.data.entity.Live

@Database(
    entities = [Live::class, Feed::class],
    version = 1,
    exportSchema = false
)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun liveDao(): LiveDao
    abstract fun feedDao(): FeedDao
}