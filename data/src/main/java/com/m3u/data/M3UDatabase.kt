package com.m3u.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.m3u.data.dao.LiveDao
import com.m3u.data.dao.SubscriptionDao
import com.m3u.data.entity.Live
import com.m3u.data.entity.Subscription
import com.m3u.data.util.LiveStateConverters

@Database(
    entities = [Live::class, Subscription::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    LiveStateConverters::class
)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun liveDao(): LiveDao
    abstract fun subscriptionDao(): SubscriptionDao
}