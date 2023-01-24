package com.m3u.data.di

import android.content.Context
import androidx.room.Room
import com.m3u.data.M3UDatabase
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
    ).build()
}