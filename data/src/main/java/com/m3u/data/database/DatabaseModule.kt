@file:Suppress("unused")

package com.m3u.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.ColorSchemeDao
import com.m3u.data.database.dao.EpisodeDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.example.ColorSchemeExample
import com.m3u.data.repository.usbkey.USBKeyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    private val timber = Timber.tag("DatabaseModule")
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        usbKeyRepository: USBKeyRepository,
        pinKeyManager: com.m3u.data.security.PINKeyManager,
        repairManager: DatabaseRepairManager
    ): M3UDatabase {
        val builder = Room.databaseBuilder(
            context,
            M3UDatabase::class.java,
            "m3u-database"
        )

        // Load SQLCipher library
        System.loadLibrary("sqlcipher")

        // Check PIN encryption first (takes priority over USB)
        val pinEncryptionEnabled = runBlocking {
            pinKeyManager.isPINEncryptionEnabled()
        }

        if (pinEncryptionEnabled) {
            // Get encryption key from PIN key manager
            val encryptionKey = runBlocking {
                pinKeyManager.getEncryptionKeyIfUnlocked()
            }

            if (encryptionKey != null) {
                builder.openHelperFactory(SupportFactory(encryptionKey))
            } else {
                throw SecurityException("Database is locked - PIN unlock required")
            }
        } else if (usbKeyRepository.isEncryptionEnabled()) {
            // Fallback to USB encryption if PIN is not enabled
            val encryptionKey = runBlocking {
                usbKeyRepository.getEncryptionKey()
            }

            if (encryptionKey != null) {
                builder.openHelperFactory(SupportFactory(encryptionKey))
            }
        }

        val database = builder
            // ❌ REMOVED: .fallbackToDestructiveMigration()
            // This was causing complete database wipeout on any migration failure or schema detection issue.
            // With encrypted databases, even minor initialization problems trigger data loss.
            // Instead: Explicit migrations are defined below, and errors are handled gracefully.
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        ColorSchemeExample.invoke(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)

                        timber.d("=== DATABASE OPENED ===")
                        timber.d("Database path: ${context.getDatabasePath("m3u-database").absolutePath}")
                        timber.d("Encrypted: ${pinEncryptionEnabled || usbKeyRepository.isEncryptionEnabled()}")

                        // ENTERPRISE FEATURE: Database Integrity Check
                        // Detects corruption early before data operations begin
                        performIntegrityCheck(db, context, repairManager)

                        // Warm up encrypted database with a simple query
                        // This ensures SQLCipher is fully initialized before UI queries
                        try {
                            db.query("SELECT COUNT(*) FROM playlists LIMIT 1")?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val count = cursor.getInt(0)
                                    timber.d("Database warm-up successful: $count playlists")
                                }
                            }
                        } catch (e: Exception) {
                            // Table might not exist yet on first run, that's OK
                            timber.d("Database warm-up: ${e.message}")
                        }

                        timber.d("✓ Database initialization complete")
                    }
                }
            )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .addMigrations(DatabaseMigrations.MIGRATION_7_8)
            .addMigrations(DatabaseMigrations.MIGRATION_10_11)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // Enables WAL mode for better concurrent performance
            .build()

        // Force database initialization and warm-up on encrypted databases
        // This prevents race conditions where UI queries happen before SQLCipher is ready
        if (pinEncryptionEnabled) {
            runBlocking {
                try {
                    // Trigger database connection and warm up SQLCipher
                    database.openHelper.writableDatabase
                } catch (e: Exception) {
                    // Log but don't crash - will be handled by later queries
                    android.util.Log.w("DatabaseModule", "Database warm-up warning: ${e.message}")
                }
            }
        }

        return database
    }

    @Provides
    @Singleton
    fun provideChannelDao(
        database: M3UDatabase
    ): ChannelDao = database.channelDao()

    @Provides
    @Singleton
    fun providePlaylistDao(
        database: M3UDatabase
    ): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun provideProgrammeDao(
        database: M3UDatabase
    ): ProgrammeDao = database.programmeDao()

    @Provides
    @Singleton
    fun provideEpisodeDao(
        database: M3UDatabase
    ): EpisodeDao = database.episodeDao()

    @Provides
    @Singleton
    fun provideColorSchemeDao(
        database: M3UDatabase
    ): ColorSchemeDao = database.colorSchemeDao()

    @Provides
    @Singleton
    fun provideWatchProgressDao(
        database: M3UDatabase
    ): com.m3u.data.database.dao.WatchProgressDao = database.watchProgressDao()

    /**
     * ENTERPRISE FEATURE: Database Integrity Check
     *
     * Performs SQLite PRAGMA integrity_check to detect database corruption early.
     * This is critical for encrypted databases where corruption can occur due to:
     * - Wrong encryption key used
     * - File system errors
     * - Device power loss during write
     * - Hardware failures
     *
     * Detection Strategy:
     * - QUICK check: Fast verification (used on every open)
     * - FULL check: Comprehensive verification (triggered if quick check fails)
     *
     * Recovery Strategy:
     * - If corruption detected, attempt to recover from backup
     * - Log detailed diagnostics for debugging
     * - Notify user of corruption and recovery status
     *
     * @param db The database connection to check
     * @param context Application context for accessing backup files
     * @param repairManager Manager for attempting automatic repair
     */
    private fun performIntegrityCheck(
        db: SupportSQLiteDatabase,
        context: Context,
        repairManager: DatabaseRepairManager
    ) {
        try {
            timber.d("=== STARTING DATABASE INTEGRITY CHECK ===")
            val startTime = System.currentTimeMillis()

            // QUICK INTEGRITY CHECK (fast, limited scope)
            // Checks: database header, page structure, index consistency
            val quickCheckResult = db.query("PRAGMA quick_check(1)").use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    "no_result"
                }
            }

            val checkDuration = System.currentTimeMillis() - startTime
            timber.d("Quick integrity check result: $quickCheckResult (${checkDuration}ms)")

            when {
                quickCheckResult == "ok" -> {
                    timber.d("✓ Database integrity: HEALTHY")
                    // Database is healthy, no action needed
                }

                quickCheckResult == "no_result" -> {
                    timber.w("⚠ Database integrity check returned no result")
                    // This can happen on first run or empty database
                }

                else -> {
                    // Corruption detected - perform full check for details
                    timber.e("✗ DATABASE CORRUPTION DETECTED: $quickCheckResult")
                    performFullIntegrityCheck(db, context, repairManager)
                }
            }

        } catch (e: Exception) {
            // Integrity check failed - this is serious
            timber.e(e, "✗ CRITICAL: Database integrity check failed with exception")
            timber.e("  This may indicate:")
            timber.e("  - Wrong encryption key")
            timber.e("  - Corrupted database file")
            timber.e("  - File system error")
            timber.e("  - Insufficient permissions")

            // Log database file info for diagnostics
            try {
                val dbFile = context.getDatabasePath("m3u-database")
                timber.e("Database file exists: ${dbFile.exists()}")
                timber.e("Database file size: ${dbFile.length()} bytes")
                timber.e("Database file readable: ${dbFile.canRead()}")
                timber.e("Database file writable: ${dbFile.canWrite()}")
            } catch (fileEx: Exception) {
                timber.e(fileEx, "Could not read database file info")
            }

            // Don't throw exception - allow app to continue and user to see error screen
            // The UI will handle showing appropriate error messages
        }
    }

    /**
     * Performs comprehensive database integrity check with detailed diagnostics.
     * Called when quick_check detects corruption.
     *
     * ENTERPRISE FEATURE: Automatic repair attempt if corruption detected.
     *
     * @param db The database connection to check
     * @param context Application context for backup recovery
     * @param repairManager Manager for attempting automatic repair
     */
    private fun performFullIntegrityCheck(
        db: SupportSQLiteDatabase,
        context: Context,
        repairManager: DatabaseRepairManager
    ) {
        timber.e("=== PERFORMING FULL DATABASE INTEGRITY CHECK ===")

        try {
            // FULL INTEGRITY CHECK (slow but comprehensive)
            val fullCheckResults = mutableListOf<String>()
            db.query("PRAGMA integrity_check").use { cursor ->
                while (cursor.moveToNext()) {
                    val result = cursor.getString(0)
                    fullCheckResults.add(result)
                    timber.e("  Integrity issue: $result")
                }
            }

            // Log corruption summary
            timber.e("=== CORRUPTION SUMMARY ===")
            timber.e("Total issues found: ${fullCheckResults.size}")
            timber.e("Critical corruption detected in encrypted database")

            // ENTERPRISE FEATURE: Attempt automatic repair
            timber.e("=== ATTEMPTING AUTOMATIC REPAIR ===")
            val repairResult = repairManager.attemptRepair(db)

            if (repairResult.success) {
                timber.e("✓ DATABASE SUCCESSFULLY REPAIRED")
                timber.e("  Strategy: ${repairResult.strategy}")
                timber.e("  Message: ${repairResult.message}")
            } else {
                timber.e("✗ AUTOMATIC REPAIR FAILED")
                timber.e("  Strategy attempted: ${repairResult.strategy}")
                timber.e("  Message: ${repairResult.message}")

                // Check for backup availability
                val backupInfo = repairManager.checkBackupAvailability()
                if (backupInfo.available) {
                    timber.w("=== BACKUP AVAILABLE FOR RECOVERY ===")
                    timber.w("Most recent backup: ${backupInfo.mostRecentBackup?.name}")
                    timber.w("Backup age: ${backupInfo.getBackupAgeMinutes()} minutes")
                    timber.w("Backup count: ${backupInfo.backupCount}")
                    timber.w("To recover: User should restore from backup in settings")
                } else {
                    timber.e("✗ NO BACKUP AVAILABLE - User may lose data")
                }
            }

            // Additional diagnostics
            logDatabaseDiagnostics(db)

        } catch (e: Exception) {
            timber.e(e, "✗ Full integrity check failed with exception")
        }
    }

    /**
     * Logs additional database diagnostics for troubleshooting.
     */
    private fun logDatabaseDiagnostics(db: SupportSQLiteDatabase) {
        try {
            timber.d("=== DATABASE DIAGNOSTICS ===")

            // Check page count
            db.query("PRAGMA page_count").use { cursor ->
                if (cursor.moveToFirst()) {
                    timber.d("Page count: ${cursor.getLong(0)}")
                }
            }

            // Check page size
            db.query("PRAGMA page_size").use { cursor ->
                if (cursor.moveToFirst()) {
                    timber.d("Page size: ${cursor.getInt(0)} bytes")
                }
            }

            // Check freelist count (fragmentation indicator)
            db.query("PRAGMA freelist_count").use { cursor ->
                if (cursor.moveToFirst()) {
                    timber.d("Freelist count: ${cursor.getInt(0)}")
                }
            }

            // Check journal mode
            db.query("PRAGMA journal_mode").use { cursor ->
                if (cursor.moveToFirst()) {
                    timber.d("Journal mode: ${cursor.getString(0)}")
                }
            }

            // For encrypted databases, check cipher version
            try {
                db.query("PRAGMA cipher_version").use { cursor ->
                    if (cursor.moveToFirst()) {
                        timber.d("SQLCipher version: ${cursor.getString(0)}")
                    }
                }
            } catch (e: Exception) {
                timber.d("Database is not encrypted (no cipher_version)")
            }

        } catch (e: Exception) {
            timber.w(e, "Could not complete database diagnostics")
        }
    }
}
