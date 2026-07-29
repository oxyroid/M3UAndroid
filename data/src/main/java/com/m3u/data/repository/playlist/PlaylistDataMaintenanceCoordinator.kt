package com.m3u.data.repository.playlist

import com.m3u.data.repository.provider.ProviderLifecycleCoordinator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Serializes playlist replacement, snapshots, deletion, and user-owned state mutations.
 *
 * WorkManager jobs run in this process, so a process-local lock is sufficient: when the process
 * stops, its workers stop as well, and WorkManager recreates both the workers and this lock before
 * resuming them. If an operation also needs [ProviderLifecycleCoordinator], acquire this
 * coordinator first and the provider or EPG coordinator second; the inverse order can deadlock
 * with restore or EPG deletion.
 */
internal object PlaylistDataMaintenanceCoordinator {
    private val mutex = Mutex()

    suspend fun <T> withExclusive(block: suspend () -> T): T {
        if (currentCoroutineContext()[MaintenanceContext] != null) {
            return block()
        }
        mutex.lock()
        return try {
            withContext(MaintenanceContext()) {
                block()
            }
        } finally {
            mutex.unlock()
        }
    }

    private class MaintenanceContext :
        AbstractCoroutineContextElement(MaintenanceContext) {
        companion object Key : CoroutineContext.Key<MaintenanceContext>
    }
}

/**
 * Prevents a refresh from recreating programme rows after its EPG source was deleted.
 */
internal object EpgDataMaintenanceCoordinator {
    private val entries = ConcurrentHashMap<String, Entry>()

    suspend fun <T> withExclusive(
        epgUrl: String,
        block: suspend () -> T,
    ): T {
        val entry = retain(epgUrl)
        var locked = false
        return try {
            entry.mutex.lock()
            locked = true
            block()
        } finally {
            if (locked) {
                entry.mutex.unlock()
            }
            release(epgUrl, entry)
        }
    }

    private fun retain(epgUrl: String): Entry = checkNotNull(
        entries.compute(epgUrl) { _, current ->
            (current ?: Entry()).also { entry ->
                entry.references.incrementAndGet()
            }
        }
    )

    private fun release(
        epgUrl: String,
        retained: Entry,
    ) {
        entries.compute(epgUrl) { _, current ->
            check(current === retained) {
                "EPG lock entry changed while it was retained"
            }
            when (val remaining = retained.references.decrementAndGet()) {
                0 -> null
                in 1..Int.MAX_VALUE -> retained
                else -> error("EPG lock entry reference count became negative")
            }
        }
    }

    private class Entry {
        val mutex = Mutex()
        val references = AtomicInteger()
    }
}
