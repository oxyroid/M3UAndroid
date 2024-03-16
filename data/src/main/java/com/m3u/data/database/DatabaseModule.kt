@file:Suppress("unused")

package com.m3u.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.database.dao.ColorPackDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.example.ColorPackExample
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
        .fallbackToDestructiveMigration()
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    ColorPackExample.invoke(db)
                }
            }
        )
        .addMigrations(DatabaseMigrations.MIGRATION_1_2)
        .addMigrations(DatabaseMigrations.MIGRATION_2_3)
        .addMigrations(DatabaseMigrations.MIGRATION_7_8)
        .addMigrations(DatabaseMigrations.MIGRATION_10_11)
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

    @Provides
    @Singleton
    fun provideColorPackDao(
        database: M3UDatabase
    ): ColorPackDao = database.colorPackDao()
}
