package com.m3u.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundedJsonlRecordStagingTest {
    private lateinit var testDirectory: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testDirectory = File(
            context.cacheDir,
            "bounded-jsonl-test-${UUID.randomUUID()}",
        )
        assertTrue(testDirectory.mkdirs())
    }

    @After
    fun tearDown() {
        BoundedJsonlRecordStaging.cleanup(testDirectory)
        testDirectory.deleteRecursively()
    }

    @Test
    fun sealedRecordsRoundTripInBoundedBatches() = runBlocking {
        val staging = createStaging(
            limits = BoundedJsonlRecordStaging.Limits(
                maximumRecords = 3,
                maximumBytes = 64,
                maximumRecordBytes = 16,
            )
        )
        try {
            assertEquals(1, staging.append("one"))
            assertEquals(2, staging.append("two"))
            assertEquals(3, staging.append("three"))
            staging.seal()

            val batches = mutableListOf<List<String>>()
            staging.forEachBatch(batchSize = 2, action = batches::add)

            assertEquals(
                listOf(listOf("one", "two"), listOf("three")),
                batches,
            )
        } finally {
            staging.close()
        }
        assertTrue(testDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun writerRejectsRecordCountRecordSizeAndTotalSizeOverflow() = runBlocking {
        val countLimited = createStaging(
            BoundedJsonlRecordStaging.Limits(
                maximumRecords = 1,
                maximumBytes = 64,
                maximumRecordBytes = 16,
            )
        )
        try {
            countLimited.append("one")
            assertFailsWithIOException { countLimited.append("two") }
        } finally {
            countLimited.close()
        }

        val recordLimited = createStaging(
            BoundedJsonlRecordStaging.Limits(
                maximumRecords = 2,
                maximumBytes = 64,
                maximumRecordBytes = 3,
            )
        )
        try {
            assertFailsWithIOException { recordLimited.append("four") }
        } finally {
            recordLimited.close()
        }

        val totalLimited = createStaging(
            BoundedJsonlRecordStaging.Limits(
                maximumRecords = 2,
                maximumBytes = 3,
                maximumRecordBytes = 3,
            )
        )
        try {
            assertFailsWithIOException { totalLimited.append("abc") }
        } finally {
            totalLimited.close()
        }

        assertTrue(testDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cancellationDuringReadStillAllowsImmediateCleanup() = runBlocking {
        val staging = createStaging(
            BoundedJsonlRecordStaging.Limits(
                maximumRecords = 2,
                maximumBytes = 64,
                maximumRecordBytes = 16,
            )
        )
        staging.append("one")
        staging.seal()
        val actionEntered = CompletableDeferred<Unit>()
        val reader = async(Dispatchers.IO) {
            try {
                staging.forEachBatch(batchSize = 1) {
                    actionEntered.complete(Unit)
                    awaitCancellation()
                }
            } finally {
                staging.close()
            }
        }

        actionEntered.await()
        reader.cancelAndJoin()

        assertTrue(testDirectory.listFiles().orEmpty().isEmpty())
    }

    private fun createStaging(
        limits: BoundedJsonlRecordStaging.Limits,
    ): BoundedJsonlRecordStaging<String> = BoundedJsonlRecordStaging.create(
        cacheDirectory = testDirectory,
        limits = limits,
        encode = { value -> value },
        decode = { encoded -> encoded },
    )

    private suspend fun assertFailsWithIOException(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IOException")
        } catch (_: IOException) {
            return
        }
    }
}
