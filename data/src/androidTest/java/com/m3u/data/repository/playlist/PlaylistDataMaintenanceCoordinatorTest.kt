package com.m3u.data.repository.playlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDataMaintenanceCoordinatorTest {
    @Test
    fun playlistMaintenanceIsSerializedAndReentrant() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val events = Collections.synchronizedList(mutableListOf<String>())

        val first = async(Dispatchers.Default) {
            PlaylistDataMaintenanceCoordinator.withExclusive {
                events += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                PlaylistDataMaintenanceCoordinator.withExclusive {
                    events += "first-nested"
                }
                events += "first-end"
            }
        }
        firstEntered.await()

        val second = async(Dispatchers.Default) {
            PlaylistDataMaintenanceCoordinator.withExclusive {
                events += "second"
                secondEntered.complete(Unit)
            }
        }

        assertNull(withTimeoutOrNull(150) { secondEntered.await() })
        releaseFirst.complete(Unit)
        withTimeout(2_000) {
            first.await()
            second.await()
        }

        assertEquals(
            listOf("first-start", "first-nested", "first-end", "second"),
            events,
        )
    }

    @Test
    fun cancelledPlaylistWaiterDoesNotBlockTheNextMaintenanceOperation() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async(Dispatchers.Default) {
            PlaylistDataMaintenanceCoordinator.withExclusive {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val cancelledWaiter = async(
            context = Dispatchers.Default,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            PlaylistDataMaintenanceCoordinator.withExclusive {
                error("A cancelled waiter must never enter the critical section")
            }
        }
        cancelledWaiter.cancelAndJoin()
        releaseFirst.complete(Unit)
        first.await()

        withTimeout(2_000) {
            PlaylistDataMaintenanceCoordinator.withExclusive {
                // Acquiring this lock proves cancellation did not strand the coordinator.
            }
        }
    }

    @Test
    fun epgMaintenanceSerializesOneSourceWithoutBlockingAnother() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val sameSourceEntered = CompletableDeferred<Unit>()
        val otherSourceEntered = CompletableDeferred<Unit>()

        val first = async(Dispatchers.Default) {
            EpgDataMaintenanceCoordinator.withExclusive("https://one.example.test/guide.xml") {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val sameSource = async(Dispatchers.Default) {
            EpgDataMaintenanceCoordinator.withExclusive("https://one.example.test/guide.xml") {
                sameSourceEntered.complete(Unit)
            }
        }
        val otherSource = async(Dispatchers.Default) {
            EpgDataMaintenanceCoordinator.withExclusive("https://two.example.test/guide.xml") {
                otherSourceEntered.complete(Unit)
            }
        }

        withTimeout(2_000) { otherSourceEntered.await() }
        assertNull(withTimeoutOrNull(150) { sameSourceEntered.await() })
        releaseFirst.complete(Unit)
        withTimeout(2_000) {
            first.await()
            sameSource.await()
            otherSource.await()
        }
        Unit
    }

    @Test
    fun cancelledEpgWaiterDoesNotStrandThePerSourceLock() = runBlocking {
        val epgUrl = "https://cancel.example.test/guide.xml"
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async(Dispatchers.Default) {
            EpgDataMaintenanceCoordinator.withExclusive(epgUrl) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val cancelledWaiter = async(
            context = Dispatchers.Default,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            EpgDataMaintenanceCoordinator.withExclusive(epgUrl) {
                error("A cancelled waiter must never enter the critical section")
            }
        }
        cancelledWaiter.cancelAndJoin()
        releaseFirst.complete(Unit)
        first.await()

        withTimeout(2_000) {
            EpgDataMaintenanceCoordinator.withExclusive(epgUrl) {
                // The reference-counted entry remains usable after a cancelled waiter.
            }
        }
    }
}
