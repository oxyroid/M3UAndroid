package com.m3u.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.m3u.data.dao.LiveDao
import com.m3u.data.dao.SubscriptionDao
import com.m3u.data.entity.Live
import com.m3u.data.entity.Subscription

@Database(
    entities = [Live::class, Subscription::class],
    version = 1,
    exportSchema = false
)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun liveDao(): LiveDao
    abstract fun subscriptionDao(): SubscriptionDao
}