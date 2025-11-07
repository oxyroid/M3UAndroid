package com.m3u.data.database

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import timber.log.Timber
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
    private val timber = Timber.tag("DatabaseMigrationHelper")

    companion object {
        private const val DATABASE_NAME = "m3u-database"
        private const val BACKUP_SUFFIX = ".backup"
        private const val TEMP_SUFFIX = ".temp-encrypted"
        private const val EXPECTED_KEY_SIZE_BYTES = 32 // 256 bits
    }

    /**
     * Migrates an existing unencrypted database to encrypted format.
     *
     * @param encryptionKey The 256-bit encryption key to use
     * @param progressCallback Optional callback for progress updates (0-100)
     * @return Result indicating success or failure with error message
     */
    suspend fun migrateToEncrypted(
        encryptionKey: ByteArray,
        progressCallback: ((Int) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        timber.d("=== STARTING DATABASE ENCRYPTION MIGRATION ===")

        return@withContext try {
            // Validate encryption key
            if (encryptionKey.size != EXPECTED_KEY_SIZE_BYTES) {
                val error = "Invalid encryption key size: ${encryptionKey.size} bytes. Expected $EXPECTED_KEY_SIZE_BYTES bytes (256 bits)"
                timber.e(error)
                return@withContext Result.failure(Exception(error))
            }
            timber.d("Encryption key validated: ${encryptionKey.size} bytes")

            // Load SQLCipher library
            timber.d("Loading SQLCipher library...")
            System.loadLibrary("sqlcipher")
            timber.d("SQLCipher library loaded successfully")

            val dbFile = context.getDatabasePath(DATABASE_NAME)
            timber.d("Database path: ${dbFile.absolutePath}")

            if (!dbFile.exists()) {
                val error = "Database file does not exist at: ${dbFile.absolutePath}"
                timber.e(error)
                return@withContext Result.failure(Exception(error))
            }
            timber.d("Database file exists, size: ${dbFile.length()} bytes")

            val backupFile = File(dbFile.parentFile, "$DATABASE_NAME$BACKUP_SUFFIX")
            val tempEncryptedFile = File(dbFile.parentFile, "$DATABASE_NAME$TEMP_SUFFIX")

            try {
                // Step 1: Create backup of original database
                timber.d("Step 1: Creating backup...")
                progressCallback?.invoke(10)
                dbFile.copyTo(backupFile, overwrite = true)
                timber.d("Backup created: ${backupFile.absolutePath}, size: ${backupFile.length()} bytes")
                progressCallback?.invoke(20)

                // Step 2: Open the unencrypted database
                timber.d("Step 2: Opening unencrypted database...")
                progressCallback?.invoke(30)

                val unencryptedDb = try {
                    SQLiteDatabase.openOrCreateDatabase(
                        dbFile.absolutePath,
                        "", // Empty passphrase for unencrypted
                        null,
                        null
                    )
                } catch (e: Exception) {
                    timber.e(e, "Failed to open unencrypted database")
                    throw Exception("Failed to open unencrypted database: ${e.message}", e)
                }

                timber.d("Unencrypted database opened successfully")
                progressCallback?.invoke(40)

                try {
                    // Step 3: Attach the new encrypted database with CORRECT SQL syntax
                    timber.d("Step 3: Attaching encrypted database...")
                    progressCallback?.invoke(50)

                    // Convert ByteArray key to hex string for SQL command
                    val keyHex = encryptionKey.joinToString("") { "%02x".format(it) }
                    timber.d("Encryption key converted to hex format (length: ${keyHex.length} chars)")

                    // CRITICAL FIX: SQLCipher KEY syntax requires x'hexstring' WITHOUT outer quotes
                    // KEY x'...' treats it as raw bytes
                    // KEY "x'...'" treats it as a text passphrase (WRONG!)
                    val attachSql = "ATTACH DATABASE '${tempEncryptedFile.absolutePath}' AS encrypted KEY x'$keyHex'"
                    timber.d("Executing ATTACH command...")
                    timber.d("SQL: $attachSql")

                    try {
                        unencryptedDb.execSQL(attachSql)
                        timber.d("ATTACH DATABASE executed successfully")
                    } catch (e: Exception) {
                        timber.e(e, "ATTACH DATABASE failed")
                        throw Exception("Failed to attach encrypted database: ${e.message}. SQL was: $attachSql", e)
                    }

                    // Step 4: Export all data to the encrypted database
                    timber.d("Step 4: Exporting data to encrypted database...")
                    progressCallback?.invoke(60)

                    try {
                        // CRITICAL FIX: Delete android_metadata table from attached database
                        // to prevent "table android_metadata already exists" error
                        // See: https://github.com/sqlcipher/android-database-sqlcipher/issues/55
                        timber.d("Deleting android_metadata table from attached database...")
                        try {
                            unencryptedDb.execSQL("DROP TABLE IF EXISTS encrypted.android_metadata")
                            timber.d("android_metadata table dropped successfully")
                        } catch (e: Exception) {
                            timber.w(e, "Failed to drop android_metadata (may not exist yet, continuing...)")
                        }

                        timber.d("Executing sqlcipher_export...")
                        // CRITICAL FIX: Use rawQuery() for SELECT statements, not execSQL()
                        // execSQL() cannot be used for queries that return results
                        val exportCursor = unencryptedDb.rawQuery("SELECT sqlcipher_export('encrypted')", null)
                        exportCursor.moveToFirst() // Execute the query
                        exportCursor.close()
                        timber.d("sqlcipher_export completed successfully")
                    } catch (e: Exception) {
                        timber.e(e, "sqlcipher_export failed")
                        throw Exception("Failed to export data to encrypted database: ${e.message}", e)
                    }

                    progressCallback?.invoke(80)

                    // Step 5: Detach the encrypted database
                    timber.d("Step 5: Detaching encrypted database...")
                    try {
                        unencryptedDb.execSQL("DETACH DATABASE encrypted")
                        timber.d("DETACH DATABASE executed successfully")
                    } catch (e: Exception) {
                        timber.e(e, "DETACH DATABASE failed (non-critical)")
                        // Non-critical error, continue
                    }
                } finally {
                    timber.d("Closing unencrypted database...")
                    unencryptedDb.close()
                    timber.d("Unencrypted database closed")
                }

                // Step 6: Verify the encrypted database can be opened
                timber.d("Step 6: Verifying encrypted database...")
                progressCallback?.invoke(85)

                if (!tempEncryptedFile.exists()) {
                    throw Exception("Encrypted database file was not created at: ${tempEncryptedFile.absolutePath}")
                }
                timber.d("Encrypted database file exists, size: ${tempEncryptedFile.length()} bytes")

                val encryptedDb = try {
                    SQLiteDatabase.openDatabase(
                        tempEncryptedFile.absolutePath,
                        encryptionKey,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                        null,
                        null
                    )
                } catch (e: Exception) {
                    timber.e(e, "Failed to open encrypted database for verification")
                    throw Exception("Failed to open encrypted database: ${e.message}. The encryption may have failed.", e)
                }

                // Verify we can read from it
                timber.d("Reading table list from encrypted database...")
                val cursor = try {
                    encryptedDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                } catch (e: Exception) {
                    encryptedDb.close()
                    timber.e(e, "Failed to query encrypted database")
                    throw Exception("Failed to read from encrypted database: ${e.message}", e)
                }

                val tableCount = cursor.count
                cursor.close()
                encryptedDb.close()
                timber.d("Encrypted database contains $tableCount tables")

                if (tableCount == 0) {
                    throw Exception("Encrypted database appears to be empty (0 tables found)")
                }

                // Step 7: Replace the original database with the encrypted one
                timber.d("Step 7: Replacing original database with encrypted version...")
                progressCallback?.invoke(90)

                if (!dbFile.delete()) {
                    throw Exception("Failed to delete original database file")
                }
                timber.d("Original database deleted")

                if (!tempEncryptedFile.renameTo(dbFile)) {
                    throw Exception("Failed to rename encrypted database to original location")
                }
                timber.d("Encrypted database renamed to original location")

                progressCallback?.invoke(95)

                // Step 8: Final verification
                timber.d("Step 8: Final verification...")
                if (!dbFile.exists()) {
                    throw Exception("Database file missing after migration")
                }
                timber.d("Final verification passed, database file size: ${dbFile.length()} bytes")

                timber.d("=== ENCRYPTION MIGRATION COMPLETED SUCCESSFULLY ===")
                Result.success(Unit)
            } catch (e: Exception) {
                timber.e(e, "Migration failed, attempting to restore from backup...")

                // Restore from backup if anything went wrong
                try {
                    if (backupFile.exists()) {
                        timber.d("Backup file exists, restoring...")

                        // Clean up failed encrypted file
                        if (tempEncryptedFile.exists()) {
                            tempEncryptedFile.delete()
                            timber.d("Deleted temporary encrypted file")
                        }

                        // Remove corrupted database
                        if (dbFile.exists()) {
                            dbFile.delete()
                            timber.d("Deleted corrupted database file")
                        }

                        // Restore backup
                        backupFile.copyTo(dbFile, overwrite = true)
                        timber.d("Backup restored successfully")
                    } else {
                        timber.e("Backup file does not exist, cannot restore!")
                    }
                } catch (restoreException: Exception) {
                    timber.e(restoreException, "CRITICAL: Failed to restore backup")
                }

                timber.e("=== ENCRYPTION MIGRATION FAILED ===")
                Result.failure(Exception("Migration failed: ${e.message}", e))
            }
        } catch (e: Exception) {
            timber.e(e, "Migration preparation failed")
            Result.failure(Exception("Migration preparation failed: ${e.message}", e))
        }
    }

    /**
     * Migrates an encrypted database back to unencrypted format.
     *
     * @param encryptionKey The current encryption key
     * @param progressCallback Optional callback for progress updates (0-100)
     * @return Result indicating success or failure
     */
    suspend fun migrateToUnencrypted(
        encryptionKey: ByteArray,
        progressCallback: ((Int) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        timber.d("=== STARTING DATABASE DECRYPTION MIGRATION ===")

        return@withContext try {
            // Validate encryption key
            if (encryptionKey.size != EXPECTED_KEY_SIZE_BYTES) {
                val error = "Invalid encryption key size: ${encryptionKey.size} bytes. Expected $EXPECTED_KEY_SIZE_BYTES bytes (256 bits)"
                timber.e(error)
                return@withContext Result.failure(Exception(error))
            }
            timber.d("Encryption key validated: ${encryptionKey.size} bytes")

            // Load SQLCipher library
            timber.d("Loading SQLCipher library...")
            System.loadLibrary("sqlcipher")
            timber.d("SQLCipher library loaded successfully")

            val dbFile = context.getDatabasePath(DATABASE_NAME)
            timber.d("Database path: ${dbFile.absolutePath}")

            if (!dbFile.exists()) {
                val error = "Database file does not exist at: ${dbFile.absolutePath}"
                timber.e(error)
                return@withContext Result.failure(Exception(error))
            }
            timber.d("Database file exists, size: ${dbFile.length()} bytes")

            val backupFile = File(dbFile.parentFile, "$DATABASE_NAME$BACKUP_SUFFIX")
            val tempUnencryptedFile = File(dbFile.parentFile, "$DATABASE_NAME$TEMP_SUFFIX")

            try {
                // Step 1: Backup current encrypted database
                timber.d("Step 1: Creating backup...")
                progressCallback?.invoke(10)
                dbFile.copyTo(backupFile, overwrite = true)
                timber.d("Backup created: ${backupFile.absolutePath}, size: ${backupFile.length()} bytes")
                progressCallback?.invoke(20)

                // Step 2: Open the encrypted database
                timber.d("Step 2: Opening encrypted database...")
                progressCallback?.invoke(30)

                val encryptedDb = try {
                    SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        encryptionKey,
                        null,
                        SQLiteDatabase.OPEN_READWRITE,
                        null,
                        null
                    )
                } catch (e: Exception) {
                    timber.e(e, "Failed to open encrypted database")
                    throw Exception("Failed to open encrypted database: ${e.message}", e)
                }

                timber.d("Encrypted database opened successfully")
                progressCallback?.invoke(40)

                try {
                    // Step 3: Attach the new unencrypted database with CORRECT SQL syntax
                    timber.d("Step 3: Attaching unencrypted database...")
                    progressCallback?.invoke(50)

                    // CRITICAL FIX: Empty string key for plaintext database (no outer quotes!)
                    val attachSql = "ATTACH DATABASE '${tempUnencryptedFile.absolutePath}' AS plaintext KEY ''"
                    timber.d("Executing ATTACH command...")
                    timber.d("SQL: $attachSql")

                    try {
                        encryptedDb.execSQL(attachSql)
                        timber.d("ATTACH DATABASE executed successfully")
                    } catch (e: Exception) {
                        timber.e(e, "ATTACH DATABASE failed")
                        throw Exception("Failed to attach unencrypted database: ${e.message}. SQL was: $attachSql", e)
                    }

                    // Step 4: Export all data to the unencrypted database
                    timber.d("Step 4: Exporting data to unencrypted database...")
                    progressCallback?.invoke(60)

                    try {
                        timber.d("Executing sqlcipher_export...")
                        // CRITICAL FIX: Use rawQuery() for SELECT statements, not execSQL()
                        val exportCursor = encryptedDb.rawQuery("SELECT sqlcipher_export('plaintext')", null)
                        exportCursor.moveToFirst() // Execute the query
                        exportCursor.close()
                        timber.d("sqlcipher_export completed successfully")
                    } catch (e: Exception) {
                        timber.e(e, "sqlcipher_export failed")
                        throw Exception("Failed to export data to unencrypted database: ${e.message}", e)
                    }

                    progressCallback?.invoke(80)

                    // Step 5: Detach
                    timber.d("Step 5: Detaching unencrypted database...")
                    try {
                        encryptedDb.execSQL("DETACH DATABASE plaintext")
                        timber.d("DETACH DATABASE executed successfully")
                    } catch (e: Exception) {
                        timber.e(e, "DETACH DATABASE failed (non-critical)")
                        // Non-critical error, continue
                    }
                } finally {
                    timber.d("Closing encrypted database...")
                    encryptedDb.close()
                    timber.d("Encrypted database closed")
                }

                // Step 6: Verify the unencrypted database
                timber.d("Step 6: Verifying unencrypted database...")
                progressCallback?.invoke(85)

                if (!tempUnencryptedFile.exists()) {
                    throw Exception("Unencrypted database file was not created at: ${tempUnencryptedFile.absolutePath}")
                }
                timber.d("Unencrypted database file exists, size: ${tempUnencryptedFile.length()} bytes")

                val unencryptedDb = try {
                    SQLiteDatabase.openOrCreateDatabase(
                        tempUnencryptedFile.absolutePath,
                        "",
                        null,
                        null
                    )
                } catch (e: Exception) {
                    timber.e(e, "Failed to open unencrypted database for verification")
                    throw Exception("Failed to open unencrypted database: ${e.message}", e)
                }

                timber.d("Reading table list from unencrypted database...")
                val cursor = try {
                    unencryptedDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                } catch (e: Exception) {
                    unencryptedDb.close()
                    timber.e(e, "Failed to query unencrypted database")
                    throw Exception("Failed to read from unencrypted database: ${e.message}", e)
                }

                val tableCount = cursor.count
                cursor.close()
                unencryptedDb.close()
                timber.d("Unencrypted database contains $tableCount tables")

                if (tableCount == 0) {
                    throw Exception("Unencrypted database appears to be empty (0 tables found)")
                }

                // Step 7: Replace with unencrypted version
                timber.d("Step 7: Replacing original database with unencrypted version...")
                progressCallback?.invoke(90)

                if (!dbFile.delete()) {
                    throw Exception("Failed to delete original database file")
                }
                timber.d("Original database deleted")

                if (!tempUnencryptedFile.renameTo(dbFile)) {
                    throw Exception("Failed to rename unencrypted database to original location")
                }
                timber.d("Unencrypted database renamed to original location")

                progressCallback?.invoke(95)

                // Step 8: Final verification
                timber.d("Step 8: Final verification...")
                if (!dbFile.exists()) {
                    throw Exception("Database file missing after migration")
                }
                timber.d("Final verification passed, database file size: ${dbFile.length()} bytes")

                timber.d("=== DECRYPTION MIGRATION COMPLETED SUCCESSFULLY ===")
                Result.success(Unit)
            } catch (e: Exception) {
                timber.e(e, "Decryption failed, attempting to restore from backup...")

                // Restore from backup if anything went wrong
                try {
                    if (backupFile.exists()) {
                        timber.d("Backup file exists, restoring...")

                        // Clean up failed unencrypted file
                        if (tempUnencryptedFile.exists()) {
                            tempUnencryptedFile.delete()
                            timber.d("Deleted temporary unencrypted file")
                        }

                        // Remove corrupted database
                        if (dbFile.exists()) {
                            dbFile.delete()
                            timber.d("Deleted corrupted database file")
                        }

                        // Restore backup
                        backupFile.copyTo(dbFile, overwrite = true)
                        timber.d("Backup restored successfully")
                    } else {
                        timber.e("Backup file does not exist, cannot restore!")
                    }
                } catch (restoreException: Exception) {
                    timber.e(restoreException, "CRITICAL: Failed to restore backup")
                }

                timber.e("=== DECRYPTION MIGRATION FAILED ===")
                Result.failure(Exception("Decryption migration failed: ${e.message}", e))
            }
        } catch (e: Exception) {
            timber.e(e, "Decryption preparation failed")
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
        timber.d("Checking if database is encrypted...")

        return@withContext try {
            System.loadLibrary("sqlcipher")
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            timber.d("Database path: ${dbFile.absolutePath}")

            if (!dbFile.exists()) {
                timber.d("Database file does not exist")
                return@withContext null
            }

            // Try to open without password
            try {
                timber.d("Attempting to open database without password...")
                val db = SQLiteDatabase.openOrCreateDatabase(
                    dbFile.absolutePath,
                    "",
                    null,
                    null
                )

                // Try to read from it
                timber.d("Attempting to read from database...")
                val cursor = db.rawQuery("SELECT name FROM sqlite_master LIMIT 1", null)
                cursor.close()
                db.close()

                // If we got here, it's unencrypted
                timber.d("Database is UNENCRYPTED (opened successfully without password)")
                false
            } catch (e: Exception) {
                // If opening without password failed, it's likely encrypted
                // (or corrupted, but we'll treat that as encrypted for safety)
                timber.d("Failed to open without password - database is ENCRYPTED or corrupted: ${e.message}")
                true
            }
        } catch (e: Exception) {
            timber.e(e, "Unable to determine encryption status")
            null
        }
    }
}
