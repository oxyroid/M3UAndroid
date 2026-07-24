package com.m3u.data.repository.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderLifecycleCoordinatorTest {
    @Test
    fun highConcurrencyForSameAccountKeyIsSerializedAndReleasesEntry() = runBlocking {
        val coordinator = ProviderLifecycleCoordinator()
        val start = CompletableDeferred<Unit>()
        val activeCount = AtomicInteger()
        val maximumActiveCount = AtomicInteger()

        val operations = List(128) {
            async(Dispatchers.Default) {
                start.await()
                coordinator.withAccount("shared-account") {
                    val active = activeCount.incrementAndGet()
                    maximumActiveCount.updateAndGet { previous -> maxOf(previous, active) }
                    try {
                        yield()
                    } finally {
                        activeCount.decrementAndGet()
                    }
                }
            }
        }
        start.complete(Unit)
        withTimeout(TEST_TIMEOUT_MILLIS) {
            operations.awaitAll()
        }

        assertEquals(1, maximumActiveCount.get())
        assertEquals(
            KeyedMutexPoolDiagnostics(entryCount = 0, referenceCount = 0),
            coordinator.lockDiagnostics().account,
        )
    }

    @Test
    fun differentAccountKeysRunInParallelAndReleaseEntries() = runBlocking {
        val coordinator = ProviderLifecycleCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val first = async(Dispatchers.Default) {
            coordinator.withAccount("account-a") {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val secondEntered = CompletableDeferred<Unit>()
        val second = async(Dispatchers.Default) {
            coordinator.withAccount("account-b") {
                secondEntered.complete(Unit)
            }
        }
        assertTrue(secondEntered.completesWithinShortWait())

        releaseFirst.complete(Unit)
        withTimeout(TEST_TIMEOUT_MILLIS) {
            first.await()
            second.await()
        }
        assertEquals(
            KeyedMutexPoolDiagnostics(entryCount = 0, referenceCount = 0),
            coordinator.lockDiagnostics().account,
        )
    }

    @Test
    fun cancelledAccountWaiterReleasesItsReferenceAndFinalEntry() = runBlocking {
        val coordinator = ProviderLifecycleCoordinator()
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        val holder = async(Dispatchers.Default) {
            coordinator.withAccount("shared-account") {
                holderEntered.complete(Unit)
                releaseHolder.await()
            }
        }
        holderEntered.await()

        val waiter = async(Dispatchers.Default) {
            coordinator.withAccount("shared-account") {
                error("Cancelled waiter must not enter the critical section")
            }
        }
        withTimeout(TEST_TIMEOUT_MILLIS) {
            while (coordinator.lockDiagnostics().account.referenceCount != 2) {
                yield()
            }
        }

        waiter.cancelAndJoin()
        assertEquals(
            KeyedMutexPoolDiagnostics(entryCount = 1, referenceCount = 1),
            coordinator.lockDiagnostics().account,
        )

        releaseHolder.complete(Unit)
        withTimeout(TEST_TIMEOUT_MILLIS) {
            holder.await()
        }
        assertEquals(
            KeyedMutexPoolDiagnostics(entryCount = 0, referenceCount = 0),
            coordinator.lockDiagnostics().account,
        )
    }

    @Test
    fun restoreAndAccountOperationsAreMutuallyExclusive() = runBlocking {
        val coordinator = ProviderLifecycleCoordinator()
        val accountEntered = CompletableDeferred<Unit>()
        val releaseAccount = CompletableDeferred<Unit>()
        val firstAccountOperation = async(Dispatchers.Default) {
            coordinator.withAccount("account-a") {
                accountEntered.complete(Unit)
                releaseAccount.await()
            }
        }
        accountEntered.await()

        val firstRestoreEntered = CompletableDeferred<Unit>()
        val firstRestoreAttempted = CompletableDeferred<Unit>()
        val firstRestore = async(Dispatchers.Default) {
            firstRestoreAttempted.complete(Unit)
            coordinator.withExclusiveRestore {
                firstRestoreEntered.complete(Unit)
            }
        }
        firstRestoreAttempted.await()
        assertFalse(firstRestoreEntered.completesWithinShortWait())

        releaseAccount.complete(Unit)
        withTimeout(TEST_TIMEOUT_MILLIS) {
            firstAccountOperation.await()
            firstRestore.await()
        }
        assertTrue(firstRestoreEntered.isCompleted)

        val secondRestoreEntered = CompletableDeferred<Unit>()
        val releaseRestore = CompletableDeferred<Unit>()
        val secondRestore = async(Dispatchers.Default) {
            coordinator.withExclusiveRestore {
                secondRestoreEntered.complete(Unit)
                releaseRestore.await()
            }
        }
        secondRestoreEntered.await()

        val secondAccountEntered = CompletableDeferred<Unit>()
        val secondAccountAttempted = CompletableDeferred<Unit>()
        val secondAccountOperation = async(Dispatchers.Default) {
            secondAccountAttempted.complete(Unit)
            coordinator.withAccount("account-a") {
                secondAccountEntered.complete(Unit)
            }
        }
        secondAccountAttempted.await()
        assertFalse(secondAccountEntered.completesWithinShortWait())

        releaseRestore.complete(Unit)
        withTimeout(TEST_TIMEOUT_MILLIS) {
            secondRestore.await()
            secondAccountOperation.await()
        }
        assertTrue(secondAccountEntered.isCompleted)
    }

    @Test
    fun nestedAccountWorkKeepsOuterOperationInsideRestoreBarrier() = runBlocking {
        val coordinator = ProviderLifecycleCoordinator()
        val nestedAccountFinished = CompletableDeferred<Unit>()
        val releaseOuterOperation = CompletableDeferred<Unit>()
        val operation = async(Dispatchers.Default) {
            coordinator.withOperation {
                coordinator.withAccount("account-a") {}
                nestedAccountFinished.complete(Unit)
                releaseOuterOperation.await()
            }
        }
        nestedAccountFinished.await()

        val restoreEntered = CompletableDeferred<Unit>()
        val restoreAttempted = CompletableDeferred<Unit>()
        val restore = async(Dispatchers.Default) {
            restoreAttempted.complete(Unit)
            coordinator.withExclusiveRestore {
                restoreEntered.complete(Unit)
            }
        }
        restoreAttempted.await()
        assertFalse(restoreEntered.completesWithinShortWait())

        releaseOuterOperation.complete(Unit)
        withTimeout(TEST_TIMEOUT_MILLIS) {
            operation.await()
            restore.await()
        }
        assertTrue(restoreEntered.isCompleted)
    }

    @Test
    fun sameRemoteIdentityIsSerializedWithoutBlockingAnotherIdentity() = runBlocking {
        val coordinator = ProviderLifecycleCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val first = async(Dispatchers.Default) {
            coordinator.withRemoteIdentity("provider", "server", "user") {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val sameIdentityEntered = CompletableDeferred<Unit>()
        val sameIdentity = async(Dispatchers.Default) {
            coordinator.withRemoteIdentity("provider", "server", "user") {
                sameIdentityEntered.complete(Unit)
            }
        }
        assertFalse(sameIdentityEntered.completesWithinShortWait())

        val otherIdentityEntered = CompletableDeferred<Unit>()
        val otherIdentity = async(Dispatchers.Default) {
            coordinator.withRemoteIdentity("provider", "server", "another-user") {
                otherIdentityEntered.complete(Unit)
            }
        }
        assertTrue(otherIdentityEntered.completesWithinShortWait())

        releaseFirst.complete(Unit)
        withTimeout(TEST_TIMEOUT_MILLIS) {
            first.await()
            sameIdentity.await()
            otherIdentity.await()
        }
        assertTrue(sameIdentityEntered.isCompleted)
        assertEquals(
            KeyedMutexPoolDiagnostics(entryCount = 0, referenceCount = 0),
            coordinator.lockDiagnostics().remoteIdentity,
        )
    }

    private suspend fun CompletableDeferred<Unit>.completesWithinShortWait(): Boolean =
        withTimeoutOrNull(SHORT_WAIT_MILLIS) {
            await()
            true
        } == true

    private companion object {
        const val SHORT_WAIT_MILLIS = 100L
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }
}
