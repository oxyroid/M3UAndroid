@file:Suppress("unused")

package com.m3u.data.database.di

import android.content.Context
import androidx.room.Room
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.dao.FeedDao
import com.m3u.data.database.dao.LiveDao
import com.m3u.data.database.dao.PostDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): M3UDatabase = Room.databaseBuilder(
        context,
        M3UDatabase::class.java,
        "m3u-database"
    )
        .addMigrations(M3UDatabase.MIGRATION_1_2)
        .build()

    @Provides
    @Singleton
    fun provideLiveDao(
        database: M3UDatabase
    ): LiveDao = database.liveDao()

    @Provides
    @Singleton
    fun provideFeedDao(
        database: M3UDatabase
    ): FeedDao = database.feedDao()

    @Provides
    @Singleton
    fun providePostDao(
        database: M3UDatabase
    ): PostDao = database.postDao()
}