package com.m3u.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupStagingFilesTest {
    private lateinit var testDirectory: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testDirectory = File(
            context.cacheDir,
            "backup-staging-test-${UUID.randomUUID()}",
        )
        assertTrue(testDirectory.mkdirs())
    }

    @After
    fun tearDown() {
        BackupStagingFiles.cleanup(testDirectory)
        testDirectory.deleteRecursively()
    }

    @Test
    fun boundedCopyRejectsInputBeyondLimit() = runBlocking {
        val destination = BackupStagingFiles.create(testDirectory)
        try {
            assertFailsWithIOException {
                BackupStagingFiles.copyBounded(
                    input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                    destination = destination,
                    maximumBytes = 3,
                )
            }
            assertTrue(destination.length() <= 3)
        } finally {
            BackupStagingFiles.release(destination)
        }
    }

    @Test
    fun lineReaderEnforcesRecordSizeAndCount() = runBlocking {
        val oversizedLine = BackupStagingFiles.create(testDirectory)
        val tooManyLines = BackupStagingFiles.create(testDirectory)
        try {
            oversizedLine.writeText("12345\n")
            assertFailsWithIOException {
                BackupStagingFiles.forEachLine(
                    file = oversizedLine,
                    maximumLineBytes = 4,
                ) {}
            }

            tooManyLines.writeText("first\r\nsecond\n")
            val accepted = mutableListOf<String>()
            assertFailsWithIOException {
                BackupStagingFiles.forEachLine(
                    file = tooManyLines,
                    maximumLines = 1,
                    action = accepted::add,
                )
            }
            assertEquals(listOf("first"), accepted)
        } finally {
            BackupStagingFiles.release(oversizedLine)
            BackupStagingFiles.release(tooManyLines)
        }
    }

    @Test
    fun cleanupKeepsActiveFilesAndDeletesOrphans() {
        val active = BackupStagingFiles.create(testDirectory)
        val orphan = File.createTempFile("m3u-restore-", ".backup", testDirectory)
        try {
            BackupStagingFiles.cleanup(testDirectory)

            assertTrue(active.exists())
            assertFalse(orphan.exists())
        } finally {
            BackupStagingFiles.release(active)
        }
        assertFalse(active.exists())
    }

    private suspend fun assertFailsWithIOException(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IOException")
        } catch (_: IOException) {
            return
        }
    }
}
