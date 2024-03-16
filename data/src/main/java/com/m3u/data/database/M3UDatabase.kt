package com.m3u.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.m3u.data.database.dao.ColorPackDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.model.ColorPack
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream

@Database(
    entities = [Stream::class, Playlist::class, ColorPack::class],
    version = 13,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
            spec = DatabaseMigrations.AutoMigration3To4::class
        ),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(
            from = 8,
            to = 9,
            spec = DatabaseMigrations.AutoMigration8To9::class
        ),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13)
    ]
)
@TypeConverters(Converters::class)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun streamDao(): StreamDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun colorPackDao(): ColorPackDao
}
