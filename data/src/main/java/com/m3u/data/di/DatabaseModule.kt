@file:Suppress("unused")

package com.m3u.data.di

import android.content.Context
import androidx.room.Room
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.StreamDao
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
        .addMigrations(M3UDatabase.MIGRATION_2_3)
        .build()

    @Provides
    @Singleton
    fun provideStreamDao(
        database: M3UDatabase
    ): StreamDao = database.streamDao()

    @Provides
    @Singleton
    fun providePlaylistDao(
        database: M3UDatabase
    ): PlaylistDao = database.playlistDao()
}
