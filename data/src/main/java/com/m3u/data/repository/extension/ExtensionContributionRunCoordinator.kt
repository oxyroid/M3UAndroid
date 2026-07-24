package com.m3u.data.repository.extension

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Prevents the immediate and periodic workers for one playlist from replacing
 * the same extension-owned metadata snapshot concurrently.
 */
@Singleton
internal class ExtensionContributionRunCoordinator @Inject constructor() {
    private val registryMonitor = Any()
    private val playlistLocks = mutableMapOf<String, LockEntry>()

    suspend fun <T> withPlaylist(
        playlistUrl: String,
        block: suspend () -> T,
    ): T = withPlaylists(listOf(playlistUrl), block)

    suspend fun <T> withPlaylists(
        playlistUrls: Collection<String>,
        block: suspend () -> T,
    ): T {
        val keys = playlistUrls
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .toList()
        val retainedLocks = retainLocks(keys)
        return try {
            withLocks(
                locks = retainedLocks.map { retained -> retained.entry.mutex },
                index = 0,
                block = block,
            )
        } finally {
            releaseLocks(retainedLocks)
        }
    }

    private suspend fun <T> withLocks(
        locks: List<Mutex>,
        index: Int,
        block: suspend () -> T,
    ): T = if (index == locks.size) {
        block()
    } else {
        locks[index].withLock {
            withLocks(locks, index + 1, block)
        }
    }

    private fun retainLocks(keys: List<String>): List<RetainedLock> =
        synchronized(registryMonitor) {
            keys.map { key ->
                val entry = playlistLocks.getOrPut(key) { LockEntry() }
                entry.retainCount += 1
                RetainedLock(key, entry)
            }
        }

    private fun releaseLocks(locks: List<RetainedLock>) {
        synchronized(registryMonitor) {
            locks.forEach { retained ->
                val current = playlistLocks[retained.key]
                check(current === retained.entry)
                current.retainCount -= 1
                check(current.retainCount >= 0)
                if (current.retainCount == 0) {
                    playlistLocks.remove(retained.key)
                }
            }
        }
    }

    internal fun retainedLockCountForTest(): Int = synchronized(registryMonitor) {
        playlistLocks.size
    }

    private class LockEntry(
        val mutex: Mutex = Mutex(),
        var retainCount: Int = 0,
    )

    private data class RetainedLock(
        val key: String,
        val entry: LockEntry,
    )
}
