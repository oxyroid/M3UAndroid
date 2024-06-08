package com.m3u.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.ColorSchemeDao
import com.m3u.data.database.dao.EpisodeDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ColorScheme
import com.m3u.data.database.model.Episode
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme

@Database(
    entities = [
        Playlist::class,
        Channel::class,
        Programme::class,
        Episode::class,
        ColorScheme::class
    ],
    version = 18,
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
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        // ver.15 is only used in a public beta deletion test package.
        AutoMigration(
            from = 14,
            to = 16,
            spec = DatabaseMigrations.AutoMigrate14To16::class
        ),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18)
    ]
)
@TypeConverters(Converters::class)
internal abstract class M3UDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun programmeDao(): ProgrammeDao
    abstract fun colorSchemeDao(): ColorSchemeDao
}
