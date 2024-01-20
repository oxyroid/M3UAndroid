package com.m3u.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream

@Database(
    entities = [Stream::class, Playlist::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
            spec = DatabaseMigrations.AutoMigration3To4::class
        ),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ]
)
abstract class M3UDatabase : RoomDatabase() {
    abstract fun streamDao(): StreamDao
    abstract fun playlistDao(): PlaylistDao
}
