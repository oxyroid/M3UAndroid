package com.m3u.data.extension.security

import com.m3u.data.database.model.ProviderAccount
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.transport.android.InstalledExtensionService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ExtensionPrincipal(
    val extensionId: ExtensionId,
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
    val uid: Int,
) {
    fun owns(account: ProviderAccount): Boolean =
        account.providerId == extensionId.value &&
            account.ownerPackageName == packageName &&
            account.ownerServiceName == serviceName &&
            account.ownerCertificateSha256 == certificateSha256
}

internal fun InstalledExtensionService.toPrincipal(extensionId: ExtensionId): ExtensionPrincipal =
    ExtensionPrincipal(
        extensionId = extensionId,
        packageName = packageName,
        serviceName = serviceName,
        certificateSha256 = certificateSha256,
        uid = uid,
    )

internal data class ExtensionPrincipalLease(
    val principal: ExtensionPrincipal,
    internal val generation: Long,
)

internal class InactiveExtensionPrincipalLeaseException : IllegalStateException(
    "Extension principal lease is no longer active"
)

/**
 * Linearizes extension lifecycle changes against provider persistence without holding a lock
 * across plugin IPC or network work.
 */
@Singleton
internal class ActiveExtensionPrincipalRegistry @Inject constructor() {
    private val stateLock = Any()
    private val slots = mutableMapOf<ExtensionId, PrincipalSlot>()

    fun activate(principal: ExtensionPrincipal) = synchronized(stateLock) {
        val slot = slotLocked(principal.extensionId)
        val previous = slot.principal
        check(previous == null || previous == principal) {
            "Extension ID is already active for another Android service"
        }
        if (previous == null) {
            slot.generation = nextGeneration(slot.generation)
            slot.principal = principal
        }
    }

    fun active(extensionId: ExtensionId): ExtensionPrincipal? = synchronized(stateLock) {
        slots[extensionId]?.let { slot ->
            if (slot.persistenceBlocked) null else slot.principal
        }
    }

    fun captureLease(extensionId: ExtensionId): ExtensionPrincipalLease? = synchronized(stateLock) {
        val slot = slots[extensionId] ?: return@synchronized null
        if (slot.persistenceBlocked) return@synchronized null
        val principal = slot.principal ?: return@synchronized null
        ExtensionPrincipalLease(principal = principal, generation = slot.generation)
    }

    fun isActive(principal: ExtensionPrincipal): Boolean = synchronized(stateLock) {
        slots[principal.extensionId]?.let { slot ->
            !slot.persistenceBlocked && slot.principal == principal
        } == true
    }

    fun deactivate(
        extensionId: ExtensionId,
        packageName: String,
        serviceName: String,
    ): ExtensionPrincipal? = synchronized(stateLock) {
        val slot = slots[extensionId] ?: return@synchronized null
        val current = slot.principal ?: return@synchronized null
        if (current.packageName != packageName || current.serviceName != serviceName) {
            return@synchronized null
        }
        slot.principal = null
        slot.generation = nextGeneration(slot.generation)
        current
    }

    suspend fun awaitPersistence(extensionId: ExtensionId) {
        val persistenceMutex = synchronized(stateLock) {
            slotLocked(extensionId).persistenceMutex
        }
        persistenceMutex.withLock { }
    }

    suspend fun <T> commit(
        lease: ExtensionPrincipalLease,
        block: suspend () -> T,
    ): T {
        val slot = synchronized(stateLock) {
            slotLocked(lease.principal.extensionId)
        }
        return slot.persistenceMutex.withLock {
            val leaseIsActive = synchronized(stateLock) {
                (
                    !slot.persistenceBlocked &&
                        slot.principal == lease.principal &&
                        slot.generation == lease.generation
                )
            }
            if (!leaseIsActive) throw InactiveExtensionPrincipalLeaseException()
            block()
        }
    }

    suspend fun <T> invalidateAndRun(
        extensionId: ExtensionId,
        block: suspend () -> T,
    ): T {
        val slot = synchronized(stateLock) { slotLocked(extensionId) }
        return slot.persistenceMutex.withLock {
            synchronized(stateLock) {
                slot.generation = nextGeneration(slot.generation)
                slot.persistenceBlocked = true
            }
            try {
                block()
            } finally {
                synchronized(stateLock) {
                    slot.persistenceBlocked = false
                }
            }
        }
    }

    private fun slotLocked(extensionId: ExtensionId): PrincipalSlot =
        slots.getOrPut(extensionId, ::PrincipalSlot)

    private fun nextGeneration(current: Long): Long = Math.addExact(current, 1L)

    private class PrincipalSlot(
        var principal: ExtensionPrincipal? = null,
        var generation: Long = 0L,
        var persistenceBlocked: Boolean = false,
        val persistenceMutex: Mutex = Mutex(),
    )
}
