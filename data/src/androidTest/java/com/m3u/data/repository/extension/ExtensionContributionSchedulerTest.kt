package com.m3u.data.repository.extension

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionContributionSchedulerTest {
    @Test
    fun enqueueUsesOpaqueNamesAndSchedulesImmediateAndPeriodicWork() = runBlocking {
        val operations = RecordingContributionWorkOperations()
        val scheduler = WorkManagerExtensionContributionScheduler(operations)
        val playlistUrl =
            "https://provider.example/get.php?username=viewer&password=top-secret"

        scheduler.enqueue(playlistUrl)

        val workKey = extensionContributionWorkKey(playlistUrl)
        assertEquals(
            listOf(extensionContributionPeriodicWorkName(workKey)),
            operations.periodic.map(PeriodicCall::name),
        )
        assertEquals(
            listOf(extensionContributionImmediateWorkName(workKey)),
            operations.immediate.map(ImmediateCall::name),
        )
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, operations.periodic.single().policy)
        assertEquals(ExistingWorkPolicy.REPLACE, operations.immediate.single().policy)
        assertEquals(
            workKey,
            operations.periodic.single().request.workSpec.input.getString(
                EXTENSION_CONTRIBUTION_INPUT_WORK_KEY
            ),
        )
        assertEquals(
            workKey,
            operations.immediate.single().request.workSpec.input.getString(
                EXTENSION_CONTRIBUTION_INPUT_WORK_KEY
            ),
        )
        (listOf(
            operations.periodic.single().request.workSpec.input.toString(),
            operations.immediate.single().request.workSpec.input.toString(),
        ) + operations.periodic.single().request.tags +
            operations.immediate.single().request.tags +
            operations.periodic.single().name +
            operations.immediate.single().name).forEach { diagnosticValue ->
            assertFalse(diagnosticValue.contains(playlistUrl))
            assertFalse(diagnosticValue.contains("viewer"))
            assertFalse(diagnosticValue.contains("top-secret"))
        }
    }

    @Test
    fun cancelWaitsForBothOpaqueWorkNames() = runBlocking {
        val operations = RecordingContributionWorkOperations()
        val scheduler = WorkManagerExtensionContributionScheduler(operations)
        val playlistUrl = "m3u-provider://account/account-1/live"
        val workKey = extensionContributionWorkKey(playlistUrl)

        scheduler.cancel(playlistUrl)

        assertEquals(
            listOf(
                extensionContributionImmediateWorkName(workKey),
                extensionContributionPeriodicWorkName(workKey),
            ),
            operations.cancelled,
        )
    }

    @Test
    fun enqueueWithoutAnEnabledContributorCancelsStaleWorkInstead() = runBlocking {
        val operations = RecordingContributionWorkOperations()
        val scheduler = WorkManagerExtensionContributionScheduler(
            operations = operations,
            hasEnabledContributor = { false },
        )
        val playlistUrl = "https://provider.example/playlist.m3u"
        val workKey = extensionContributionWorkKey(playlistUrl)

        scheduler.enqueue(playlistUrl)

        assertTrue(operations.periodic.isEmpty())
        assertTrue(operations.immediate.isEmpty())
        assertEquals(
            listOf(
                extensionContributionImmediateWorkName(workKey),
                extensionContributionPeriodicWorkName(workKey),
            ),
            operations.cancelled,
        )
    }

    @Test
    fun contributorDisabledDuringEnqueueCannotLeaveNewWorkBehind() = runBlocking {
        val operations = RecordingContributionWorkOperations()
        var eligibilityCheck = 0
        val scheduler = WorkManagerExtensionContributionScheduler(
            operations = operations,
            hasEnabledContributor = {
                eligibilityCheck++ == 0
            },
        )
        val playlistUrl = "https://provider.example/playlist.m3u"
        val workKey = extensionContributionWorkKey(playlistUrl)

        scheduler.enqueue(playlistUrl)

        assertEquals(1, operations.periodic.size)
        assertEquals(1, operations.immediate.size)
        assertEquals(
            listOf(
                extensionContributionImmediateWorkName(workKey),
                extensionContributionPeriodicWorkName(workKey),
            ),
            operations.cancelled,
        )
    }

    @Test
    fun schedulingFailureIsNotReportedAsSuccess() = runBlocking {
        val expected = IOException("work database unavailable")
        val operations = RecordingContributionWorkOperations(periodicFailure = expected)
        val scheduler = WorkManagerExtensionContributionScheduler(operations)

        val actual = runCatching {
            scheduler.enqueue("https://provider.example/playlist.m3u")
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertTrue(operations.immediate.isEmpty())
    }

    @Test
    fun immediateSchedulingFailureRollsBackPeriodicWork() = runBlocking {
        val expected = IOException("immediate scheduling failed")
        val operations = RecordingContributionWorkOperations(immediateFailure = expected)
        val scheduler = WorkManagerExtensionContributionScheduler(operations)
        val playlistUrl = "https://provider.example/playlist.m3u"
        val workKey = extensionContributionWorkKey(playlistUrl)

        val actual = runCatching {
            scheduler.enqueue(playlistUrl)
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertEquals(
            listOf(extensionContributionPeriodicWorkName(workKey)),
            operations.cancelled,
        )
    }

    @Test
    fun cancellationAttemptsBothWorkNamesWhenTheFirstCancellationFails() = runBlocking {
        val expected = IOException("immediate cancellation failed")
        val operations = RecordingContributionWorkOperations(
            cancellationFailureAtCall = 1,
            cancellationFailure = expected,
        )
        val scheduler = WorkManagerExtensionContributionScheduler(operations)
        val playlistUrl = "https://provider.example/playlist.m3u"
        val workKey = extensionContributionWorkKey(playlistUrl)

        val actual = runCatching {
            scheduler.cancel(playlistUrl)
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertEquals(
            listOf(
                extensionContributionImmediateWorkName(workKey),
                extensionContributionPeriodicWorkName(workKey),
            ),
            operations.cancelled,
        )
    }

    @Test
    fun oversizedPlaylistUrlIsRejectedBeforeCreatingWork() = runBlocking {
        val operations = RecordingContributionWorkOperations()
        val scheduler = WorkManagerExtensionContributionScheduler(operations)

        val failure = runCatching {
            scheduler.enqueue("https://example.test/" + "a".repeat(9 * 1024))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(operations.periodic.isEmpty())
        assertTrue(operations.immediate.isEmpty())
    }

    @Test
    fun coordinatorSerializesWorkForOnePlaylist() = runBlocking {
        val coordinator = ExtensionContributionRunCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val first = async {
            coordinator.withPlaylist("playlist") {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()
        val second = async {
            coordinator.withPlaylist("playlist") {
                secondEntered.complete(Unit)
            }
        }

        assertFalse(secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertTrue(secondEntered.isCompleted)
    }

    @Test
    fun coordinatorSerializesMultiPlaylistImportWithSinglePlaylistRefresh() = runBlocking {
        val coordinator = ExtensionContributionRunCoordinator()
        val importEntered = CompletableDeferred<Unit>()
        val releaseImport = CompletableDeferred<Unit>()
        val refreshEntered = CompletableDeferred<Unit>()

        val import = async {
            coordinator.withPlaylists(listOf("series", "live", "vod")) {
                importEntered.complete(Unit)
                releaseImport.await()
            }
        }
        importEntered.await()
        val refresh = async {
            coordinator.withPlaylist("live") {
                refreshEntered.complete(Unit)
            }
        }

        assertFalse(refreshEntered.isCompleted)
        releaseImport.complete(Unit)
        import.await()
        refresh.await()
        assertTrue(refreshEntered.isCompleted)
    }

    @Test
    fun coordinatorUsesOneOrderForOverlappingMultiPlaylistImports() = runBlocking {
        val coordinator = ExtensionContributionRunCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val first = async {
            coordinator.withPlaylists(listOf("old-url", "new-url")) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()
        val second = async {
            coordinator.withPlaylists(listOf("new-url", "old-url")) {
                secondEntered.complete(Unit)
            }
        }

        assertFalse(secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        awaitAll(first, second)
        assertTrue(secondEntered.isCompleted)
    }

    @Test
    fun coordinatorMigrationLockBlocksWorkersUsingOldAndNewPlaylistKeys() = runBlocking {
        val coordinator = ExtensionContributionRunCoordinator()
        val migrationEntered = CompletableDeferred<Unit>()
        val releaseMigration = CompletableDeferred<Unit>()
        val oldWorkerEntered = CompletableDeferred<Unit>()
        val newWorkerEntered = CompletableDeferred<Unit>()

        val migration = async {
            coordinator.withPlaylists(listOf("content://playlist", "file://playlist")) {
                migrationEntered.complete(Unit)
                releaseMigration.await()
            }
        }
        migrationEntered.await()
        val workers = listOf(
            async {
                coordinator.withPlaylist("content://playlist") {
                    oldWorkerEntered.complete(Unit)
                }
            },
            async {
                coordinator.withPlaylist("file://playlist") {
                    newWorkerEntered.complete(Unit)
                }
            },
        )

        assertFalse(oldWorkerEntered.isCompleted)
        assertFalse(newWorkerEntered.isCompleted)
        releaseMigration.complete(Unit)
        awaitAll(migration, *workers.toTypedArray())
        assertTrue(oldWorkerEntered.isCompleted)
        assertTrue(newWorkerEntered.isCompleted)
    }

    @Test
    fun coordinatorReleasesUnusedPlaylistLockEntries() = runBlocking {
        val coordinator = ExtensionContributionRunCoordinator()

        repeat(1_000) { index ->
            coordinator.withPlaylist("playlist-$index") {}
        }

        assertEquals(0, coordinator.retainedLockCountForTest())
    }

    private data class ImmediateCall(
        val name: String,
        val policy: ExistingWorkPolicy,
        val request: OneTimeWorkRequest,
    )

    private data class PeriodicCall(
        val name: String,
        val policy: ExistingPeriodicWorkPolicy,
        val request: PeriodicWorkRequest,
    )

    private class RecordingContributionWorkOperations(
        private val periodicFailure: Exception? = null,
        private val immediateFailure: Exception? = null,
        private val cancellationFailureAtCall: Int? = null,
        private val cancellationFailure: Exception? = null,
    ) : ExtensionContributionWorkOperations {
        val immediate = mutableListOf<ImmediateCall>()
        val periodic = mutableListOf<PeriodicCall>()
        val cancelled = mutableListOf<String>()

        override suspend fun enqueueUniqueWork(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ) {
            immediateFailure?.let { throw it }
            immediate += ImmediateCall(uniqueWorkName, policy, request)
        }

        override suspend fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) {
            periodicFailure?.let { throw it }
            periodic += PeriodicCall(uniqueWorkName, policy, request)
        }

        override suspend fun cancelUniqueWork(uniqueWorkName: String) {
            cancelled += uniqueWorkName
            if (cancelled.size == cancellationFailureAtCall) {
                throw checkNotNull(cancellationFailure)
            }
        }
    }
}
