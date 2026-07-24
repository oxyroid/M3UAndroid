package com.m3u.data.repository.provider

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates normal provider work with rare whole-database restore operations.
 *
 * Normal operations may run concurrently for different accounts, while a restore closes the
 * entrance gate and waits for every active operation before mutating provider state.
 */
@Singleton
internal class ProviderLifecycleCoordinator @Inject constructor() {
    private val entrance = Mutex()
    private val operationPermits = Semaphore(MAX_CONCURRENT_OPERATIONS)
    private val accountLocks = KeyedMutexPool<String>()
    private val remoteIdentityLocks = KeyedMutexPool<RemoteIdentity>()

    suspend fun <T> withOperation(block: suspend () -> T): T {
        val inherited = currentCoroutineContext()[ProviderOperationContext]
        if (inherited?.contains(this) == true) return block()
        acquireOperationPermit()
        return try {
            withContext(
                inherited?.adding(this) ?: ProviderOperationContext(setOf(this))
            ) {
                block()
            }
        } finally {
            operationPermits.release()
        }
    }

    suspend fun <T> withAccount(
        accountId: String,
        block: suspend () -> T,
    ): T = withOperation { accountLocks.withLock(accountId, block) }

    suspend fun <T> withRemoteIdentity(
        providerId: String,
        serverId: String,
        userId: String,
        block: suspend () -> T,
    ): T = withOperation {
        val identity = RemoteIdentity(
            providerId = providerId,
            serverId = serverId,
            userId = userId,
        )
        remoteIdentityLocks.withLock(identity, block)
    }

    suspend fun <T> withExclusiveRestore(block: suspend () -> T): T {
        check(currentCoroutineContext()[ProviderOperationContext]?.contains(this) != true) {
            "A provider restore cannot start inside a provider operation"
        }
        entrance.lock()
        var acquiredPermits = 0
        return try {
            repeat(MAX_CONCURRENT_OPERATIONS) {
                operationPermits.acquire()
                acquiredPermits++
            }
            block()
        } finally {
            repeat(acquiredPermits) {
                operationPermits.release()
            }
            entrance.unlock()
        }
    }

    private suspend fun acquireOperationPermit() {
        entrance.lock()
        try {
            operationPermits.acquire()
        } finally {
            entrance.unlock()
        }
    }

    internal fun lockDiagnostics(): ProviderLifecycleLockDiagnostics =
        ProviderLifecycleLockDiagnostics(
            account = accountLocks.diagnostics(),
            remoteIdentity = remoteIdentityLocks.diagnostics(),
        )

    private companion object {
        const val MAX_CONCURRENT_OPERATIONS = 64
    }
}

internal data class ProviderLifecycleLockDiagnostics(
    val account: KeyedMutexPoolDiagnostics,
    val remoteIdentity: KeyedMutexPoolDiagnostics,
)

internal data class KeyedMutexPoolDiagnostics(
    val entryCount: Int,
    val referenceCount: Int,
)

private class KeyedMutexPool<Key : Any> {
    private val entries = ConcurrentHashMap<Key, Entry>()

    suspend fun <T> withLock(
        key: Key,
        block: suspend () -> T,
    ): T {
        val entry = retain(key)
        return try {
            entry.mutex.withLock { block() }
        } finally {
            release(key, entry)
        }
    }

    fun diagnostics(): KeyedMutexPoolDiagnostics = KeyedMutexPoolDiagnostics(
        entryCount = entries.size,
        referenceCount = entries.values.sumOf { entry -> entry.references.get() },
    )

    private fun retain(key: Key): Entry = checkNotNull(
        entries.compute(key) { _, current ->
            (current ?: Entry()).also { entry ->
                entry.references.incrementAndGet()
            }
        }
    )

    private fun release(key: Key, retained: Entry) {
        entries.compute(key) { _, current ->
            check(current === retained) {
                "Keyed lock entry changed while it was still retained"
            }
            when (val remaining = retained.references.decrementAndGet()) {
                0 -> null
                in 1..Int.MAX_VALUE -> retained
                else -> error("Keyed lock entry reference count became negative")
            }
        }
    }

    private class Entry {
        val mutex = Mutex()
        val references = AtomicInteger()
    }
}

private data class RemoteIdentity(
    val providerId: String,
    val serverId: String,
    val userId: String,
)

private class ProviderOperationContext(
    private val coordinators: Set<ProviderLifecycleCoordinator>,
) : AbstractCoroutineContextElement(ProviderOperationContext) {
    fun contains(coordinator: ProviderLifecycleCoordinator): Boolean =
        coordinator in coordinators

    fun adding(coordinator: ProviderLifecycleCoordinator): ProviderOperationContext =
        ProviderOperationContext(coordinators + coordinator)

    companion object Key : CoroutineContext.Key<ProviderOperationContext>
}
