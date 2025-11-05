package com.m3u.data.database

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for migrating an unencrypted Room database to an encrypted one.
 *
 * This handles the critical operation of:
 * 1. Backing up the existing unencrypted database
 * 2. Creating a new encrypted database with the same schema
 * 3. Copying all data from the old database to the new encrypted one
 * 4. Replacing the original database file
 *
 * The operation is atomic - if anything fails, the original database is restored.
 */
@Singleton
class DatabaseMigrationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DATABASE_NAME = "m3u-database"
        private const val BACKUP_SUFFIX = ".backup"
        private const val TEMP_SUFFIX = ".temp-encrypted"
    }

    /**
     * Migrates an existing unencrypted database to encrypted format.
     *
     * @param encryptionKey The 256-bit encryption key to use
     * @return Result indicating success or failure with error message
     */
    suspend fun migrateToEncrypted(encryptionKey: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Load SQLCipher library
            System.loadLibrary("sqlcipher")

            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Database file does not exist"))
            }

            val backupFile = File(dbFile.parentFile, "$DATABASE_NAME$BACKUP_SUFFIX")
            val tempEncryptedFile = File(dbFile.parentFile, "$DATABASE_NAME$TEMP_SUFFIX")

            try {
                // Step 1: Create backup of original database
                dbFile.copyTo(backupFile, overwrite = true)

                // Step 2: Open the unencrypted database
                // Step 3: Use SQLCipher's built-in export functionality
                // This is more reliable than manual copying
                val unencryptedDb = SQLiteDatabase.openOrCreateDatabase(
                    dbFile.absolutePath,
                    "", // Empty passphrase for unencrypted
                    null,
                    null
                )

                try {
                    // Attach the new encrypted database
                    // Convert ByteArray key to hex string for SQL command
                    val keyHex = encryptionKey.joinToString("") { "%02x".format(it) }
                    unencryptedDb.execSQL("ATTACH DATABASE '${tempEncryptedFile.absolutePath}' AS encrypted KEY 'x''$keyHex''")

                    // Export all data to the encrypted database
                    unencryptedDb.execSQL("SELECT sqlcipher_export('encrypted')")

                    // Detach the encrypted database
                    unencryptedDb.execSQL("DETACH DATABASE encrypted")
                } finally {
                    unencryptedDb.close()
                }

                // Step 4: Verify the encrypted database can be opened
                val encryptedDb = SQLiteDatabase.openDatabase(
                    tempEncryptedFile.absolutePath,
                    encryptionKey,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    null,
                    null
                )

                // Verify we can read from it
                val cursor = encryptedDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                val tableCount = cursor.count
                cursor.close()
                encryptedDb.close()

                if (tableCount == 0) {
                    throw Exception("Encrypted database appears to be empty")
                }

                // Step 5: Replace the original database with the encrypted one
                dbFile.delete()
                tempEncryptedFile.renameTo(dbFile)

                // Step 6: Clean up backup (keep it for one more operation in case of issues)
                // We don't delete the backup here - let the repository handle it after verification

                Result.success(Unit)
            } catch (e: Exception) {
                // Restore from backup if anything went wrong
                if (backupFile.exists()) {
                    tempEncryptedFile.delete()
                    dbFile.delete()
                    backupFile.copyTo(dbFile, overwrite = true)
                }
                Result.failure(Exception("Migration failed: ${e.message}", e))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Migration preparation failed: ${e.message}", e))
        }
    }

    /**
     * Migrates an encrypted database back to unencrypted format.
     *
     * @param encryptionKey The current encryption key
     * @return Result indicating success or failure
     */
    suspend fun migrateToUnencrypted(encryptionKey: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            System.loadLibrary("sqlcipher")

            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Database file does not exist"))
            }

            val backupFile = File(dbFile.parentFile, "$DATABASE_NAME$BACKUP_SUFFIX")
            val tempUnencryptedFile = File(dbFile.parentFile, "$DATABASE_NAME$TEMP_SUFFIX")

            try {
                // Backup current encrypted database
                dbFile.copyTo(backupFile, overwrite = true)

                // Open the encrypted database
                val encryptedDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    encryptionKey,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null,
                    null
                )

                try {
                    // Attach the new unencrypted database
                    encryptedDb.execSQL("ATTACH DATABASE '${tempUnencryptedFile.absolutePath}' AS plaintext KEY ''")

                    // Export all data to the unencrypted database
                    encryptedDb.execSQL("SELECT sqlcipher_export('plaintext')")

                    // Detach
                    encryptedDb.execSQL("DETACH DATABASE plaintext")
                } finally {
                    encryptedDb.close()
                }

                // Verify the unencrypted database
                val unencryptedDb = SQLiteDatabase.openOrCreateDatabase(
                    tempUnencryptedFile.absolutePath,
                    "",
                    null,
                    null
                )
                val cursor = unencryptedDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                val tableCount = cursor.count
                cursor.close()
                unencryptedDb.close()

                if (tableCount == 0) {
                    throw Exception("Unencrypted database appears to be empty")
                }

                // Replace with unencrypted version
                dbFile.delete()
                tempUnencryptedFile.renameTo(dbFile)

                Result.success(Unit)
            } catch (e: Exception) {
                // Restore from backup
                if (backupFile.exists()) {
                    tempUnencryptedFile.delete()
                    dbFile.delete()
                    backupFile.copyTo(dbFile, overwrite = true)
                }
                Result.failure(Exception("Decryption migration failed: ${e.message}", e))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Decryption preparation failed: ${e.message}", e))
        }
    }

    /**
     * Cleans up backup files after successful migration verification.
     */
    suspend fun cleanupBackups(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val backupFile = File(context.getDatabasePath(DATABASE_NAME).parentFile, "$DATABASE_NAME$BACKUP_SUFFIX")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if the database is currently encrypted by attempting to read it.
     *
     * @return true if encrypted, false if unencrypted, null if unable to determine
     */
    suspend fun isDatabaseEncrypted(): Boolean? = withContext(Dispatchers.IO) {
        return@withContext try {
            System.loadLibrary("sqlcipher")
            val dbFile = context.getDatabasePath(DATABASE_NAME)

            if (!dbFile.exists()) {
                return@withContext null
            }

            // Try to open without password
            try {
                val db = SQLiteDatabase.openOrCreateDatabase(
                    dbFile.absolutePath,
                    "",
                    null,
                    null
                )

                // Try to read from it
                val cursor = db.rawQuery("SELECT name FROM sqlite_master LIMIT 1", null)
                cursor.close()
                db.close()

                // If we got here, it's unencrypted
                false
            } catch (e: Exception) {
                // If opening without password failed, it's likely encrypted
                // (or corrupted, but we'll treat that as encrypted for safety)
                true
            }
        } catch (e: Exception) {
            null
        }
    }
}
