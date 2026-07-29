package com.m3u.data.worker

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.work.WorkManager

/**
 * Durable ownership metadata for URI grants temporarily held by playlist workers.
 *
 * A pending lease is written before the platform grant is acquired. Cleanup compares the stored
 * generation again while holding the same process lock immediately before releasing a grant, so a
 * new picker result cannot be released by an older cleanup pass.
 */
internal object PersistedUriPermissionLeaseStore {
    private val lock = Any()
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            val appContext = context.applicationContext
            applicationContext = appContext
            val preferences = appContext.preferences()
            if (!preferences.getBoolean(KEY_MIGRATED, false)) {
                /*
                 * Grants created by older releases are deliberately left unowned. Some of them
                 * still back a content:// playlist row and must remain readable until that
                 * playlist is refreshed into app-owned storage. Only grants explicitly begun by
                 * this lease store are safe for its cleanup worker to release.
                 */
                preferences.edit().putBoolean(KEY_MIGRATED, true).commit()
            }
        }
    }

    fun begin(
        context: Context,
        permissionTag: String,
    ) {
        initialize(context)
        synchronized(lock) {
            val preferences = requireContext().preferences()
            val current = preferences.readEntry(permissionTag)
                ?: LeaseEntry(generation = 0L, pendingCount = 0, touchedAtMillis = 0L)
            preferences.edit()
                .putString(
                    permissionTag.entryKey(),
                    current.copy(
                        generation = current.generation + 1L,
                        pendingCount = current.pendingCount + 1,
                        touchedAtMillis = System.currentTimeMillis(),
                    ).encode(),
                )
                .commit()
        }
    }

    fun finishPending(permissionTag: String) {
        synchronized(lock) {
            val preferences = applicationContext?.preferences() ?: return
            val current = preferences.readEntry(permissionTag) ?: return
            preferences.edit()
                .putString(
                    permissionTag.entryKey(),
                    current.copy(
                        generation = current.generation + 1L,
                        pendingCount = (current.pendingCount - 1).coerceAtLeast(0),
                        touchedAtMillis = System.currentTimeMillis(),
                    ).encode(),
                )
                .commit()
        }
    }

    fun ownedTags(context: Context): List<String> {
        initialize(context)
        return synchronized(lock) {
            requireContext().preferences().all.keys
                .asSequence()
                .filter { key -> key.startsWith(ENTRY_PREFIX) }
                .map { key -> key.removePrefix(ENTRY_PREFIX) }
                .toList()
        }
    }

    fun snapshot(
        context: Context,
        permissionTag: String,
    ): LeaseSnapshot? {
        initialize(context)
        return synchronized(lock) {
            val preferences = requireContext().preferences()
            val entry = preferences.readEntry(permissionTag) ?: return@synchronized null
            val now = System.currentTimeMillis()
            val normalized = if (
                entry.pendingCount > 0 &&
                now - entry.touchedAtMillis >= ABANDONED_PENDING_MILLIS
            ) {
                entry.copy(
                    generation = entry.generation + 1L,
                    pendingCount = 0,
                    touchedAtMillis = now,
                ).also { updated ->
                    preferences.edit()
                        .putString(permissionTag.entryKey(), updated.encode())
                        .commit()
                }
            } else {
                entry
            }
            normalized.toSnapshot()
        }
    }

    fun releaseIfUnchanged(
        context: Context,
        permissionTag: String,
        generation: Long,
        release: () -> Unit,
    ): Boolean {
        initialize(context)
        return synchronized(lock) {
            val preferences = requireContext().preferences()
            val current = preferences.readEntry(permissionTag) ?: return@synchronized true
            if (current.generation != generation || current.pendingCount != 0) {
                return@synchronized false
            }
            release()
            preferences.edit().remove(permissionTag.entryKey()).commit()
            true
        }
    }

    private fun requireContext(): Context =
        checkNotNull(applicationContext) { "Persisted URI lease store is not initialized" }

    private fun Context.preferences() =
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun SharedPreferences.readEntry(
        permissionTag: String,
    ): LeaseEntry? = getString(permissionTag.entryKey(), null)?.let(LeaseEntry::decodeOrNull)

    private fun String.entryKey(): String = "$ENTRY_PREFIX$this"

    data class LeaseSnapshot(
        val generation: Long,
        val pendingCount: Int,
    )

    private data class LeaseEntry(
        val generation: Long,
        val pendingCount: Int,
        val touchedAtMillis: Long,
    ) {
        fun encode(): String = "$generation,$pendingCount,$touchedAtMillis"

        fun toSnapshot(): LeaseSnapshot = LeaseSnapshot(
            generation = generation,
            pendingCount = pendingCount,
        )

        companion object {
            fun decodeOrNull(encoded: String): LeaseEntry? {
                val parts = encoded.split(',')
                if (parts.size != 3) return null
                val generation = parts[0].toLongOrNull() ?: return null
                val pendingCount = parts[1].toIntOrNull() ?: return null
                val touchedAtMillis = parts[2].toLongOrNull() ?: return null
                if (generation < 0L || pendingCount < 0 || touchedAtMillis < 0L) return null
                return LeaseEntry(generation, pendingCount, touchedAtMillis)
            }
        }
    }

    private const val PREFERENCES_NAME = "playlist-uri-permission-leases"
    private const val ENTRY_PREFIX = "lease:"
    private const val KEY_MIGRATED = "migrated-v1"
    private const val ABANDONED_PENDING_MILLIS = 5L * 60L * 1_000L
}

fun beginPersistedUriPermissionLease(
    context: Context,
    uri: Uri,
): String = persistedUriPermissionTag(uri).also { permissionTag ->
    PersistedUriPermissionLeaseStore.begin(context, permissionTag)
}

fun abandonPersistedUriPermissionLease(
    context: Context,
    permissionTag: String,
) {
    PersistedUriPermissionLeaseStore.initialize(context)
    PersistedUriPermissionLeaseStore.finishPending(permissionTag)
    PersistedUriPermissionCleanupWorker.enqueue(
        workManager = WorkManager.getInstance(context),
        permissionTag = permissionTag,
    )
}

fun initializePersistedUriPermissionLeases(context: Context) {
    PersistedUriPermissionLeaseStore.initialize(context)
}
