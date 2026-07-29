package com.m3u.data.worker

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ProgressUpdater
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val PERMISSION_TAG_INPUT = "permission-tag"
private const val TEST_PROVIDER_URI = "content://com.m3u.data.test.persistable-uri"
private const val TEST_PROVIDER_METHOD_GRANT = "grant"
private const val TEST_PROVIDER_METHOD_REVOKE = "revoke"
private const val TEST_PROVIDER_URI_EXTRA = "uri"

@RunWith(AndroidJUnit4::class)
class PersistedUriPermissionCleanupWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workManager = WorkManager.getInstance(context)

    @Test
    fun ownedPermissionIsKeptForUnfinishedWorkAndReleasedAfterCancellation() = runBlocking {
        val uri = Uri.parse("$TEST_PROVIDER_URI/${UUID.randomUUID()}")
        val permissionTag = beginPersistedUriPermissionLease(context, uri)
        val request = OneTimeWorkRequestBuilder<DelayedPermissionOwnerWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .addTag(permissionTag)
            .build()

        grantPersistableReadPermission(uri)
        try {
            workManager.enqueue(request).result.get(5, TimeUnit.SECONDS)
            PersistedUriPermissionLeaseStore.finishPending(permissionTag)

            val whilePending = cleanupWorker(permissionTag).doWork()

            assertEquals(ListenableWorker.Result.retry(), whilePending)
            assertTrue(context.contentResolver.hasPersistedPermission(uri))

            workManager.cancelWorkById(request.id).result.get(5, TimeUnit.SECONDS)
            val afterCancellation = cleanupWorker(permissionTag).doWork()

            assertEquals(ListenableWorker.Result.success(), afterCancellation)
            assertFalse(context.contentResolver.hasPersistedPermission(uri))
        } finally {
            workManager.cancelWorkById(request.id).result.get(5, TimeUnit.SECONDS)
            forgetOwnedLease(permissionTag)
            releasePersistableReadPermissionIfPresent(uri)
            revokeProviderGrant(uri)
        }
    }

    @Test
    fun ownedPermissionIsReleasedAfterTaggedWorkCompletes() = runBlocking {
        val uri = Uri.parse("$TEST_PROVIDER_URI/${UUID.randomUUID()}")
        val permissionTag = beginPersistedUriPermissionLease(context, uri)
        val request = OneTimeWorkRequestBuilder<CompletingPermissionOwnerWorker>()
            .addTag(permissionTag)
            .build()

        grantPersistableReadPermission(uri)
        try {
            workManager.enqueue(request).result.get(5, TimeUnit.SECONDS)
            PersistedUriPermissionLeaseStore.finishPending(permissionTag)
            val finished = withTimeout(5_000) {
                workManager.getWorkInfoByIdFlow(request.id)
                    .filterNotNull()
                    .first { workInfo -> workInfo.state.isFinished }
            }

            assertEquals(WorkInfo.State.SUCCEEDED, finished.state)

            val afterCompletion = cleanupWorker(permissionTag).doWork()

            assertEquals(ListenableWorker.Result.success(), afterCompletion)
            assertFalse(context.contentResolver.hasPersistedPermission(uri))
        } finally {
            workManager.cancelWorkById(request.id).result.get(5, TimeUnit.SECONDS)
            forgetOwnedLease(permissionTag)
            releasePersistableReadPermissionIfPresent(uri)
            revokeProviderGrant(uri)
        }
    }

    @Test
    fun persistedGrantWithoutAnOwnershipLeaseIsNotReleased() = runBlocking {
        val uri = Uri.parse("$TEST_PROVIDER_URI/${UUID.randomUUID()}")
        val permissionTag = persistedUriPermissionTag(uri)
        initializePersistedUriPermissionLeases(context)
        grantPersistableReadPermission(uri)
        try {
            val cleanupResult = cleanupWorker(permissionTag).doWork()

            assertEquals(ListenableWorker.Result.success(), cleanupResult)
            assertTrue(context.contentResolver.hasPersistedPermission(uri))
            assertFalse(
                PersistedUriPermissionLeaseStore.ownedTags(context).contains(permissionTag),
            )
        } finally {
            releasePersistableReadPermissionIfPresent(uri)
            revokeProviderGrant(uri)
        }
    }

    private fun cleanupWorker(permissionTag: String): PersistedUriPermissionCleanupWorker =
        PersistedUriPermissionCleanupWorker(
            context = context,
            params = workerParameters(permissionTag),
            workManager = workManager,
        )

    private fun workerParameters(permissionTag: String): WorkerParameters {
        val implementation = workManager as WorkManagerImpl
        return WorkerParameters(
            UUID.randomUUID(),
            workDataOf(PERMISSION_TAG_INPUT to permissionTag),
            emptySet(),
            WorkerParameters.RuntimeExtras(),
            0,
            0,
            implementation.configuration.executor,
            implementation.configuration.workerCoroutineContext,
            implementation.workTaskExecutor,
            implementation.configuration.workerFactory,
            ProgressUpdater { _, _, _ -> Futures.immediateVoidFuture() },
            ForegroundUpdater { _, _, _ -> Futures.immediateVoidFuture() },
        )
    }

    private fun grantPersistableReadPermission(uri: Uri) {
        context.contentResolver.call(
            uri,
            TEST_PROVIDER_METHOD_GRANT,
            context.packageName,
            Bundle().apply {
                putString(TEST_PROVIDER_URI_EXTRA, uri.toString())
            },
        )
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    private fun releasePersistableReadPermissionIfPresent(uri: Uri) {
        if (context.contentResolver.hasPersistedPermission(uri)) {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun forgetOwnedLease(permissionTag: String) {
        var lease = PersistedUriPermissionLeaseStore.snapshot(context, permissionTag) ?: return
        while (lease.pendingCount > 0) {
            PersistedUriPermissionLeaseStore.finishPending(permissionTag)
            lease = PersistedUriPermissionLeaseStore.snapshot(context, permissionTag) ?: return
        }
        PersistedUriPermissionLeaseStore.releaseIfUnchanged(
            context = context,
            permissionTag = permissionTag,
            generation = lease.generation,
            release = {},
        )
    }

    private fun revokeProviderGrant(uri: Uri) {
        context.contentResolver.call(
            uri,
            TEST_PROVIDER_METHOD_REVOKE,
            null,
            Bundle().apply {
                putString(TEST_PROVIDER_URI_EXTRA, uri.toString())
            },
        )
    }

    private fun ContentResolver.hasPersistedPermission(uri: Uri): Boolean =
        persistedUriPermissions.any { permission -> permission.uri == uri }

    private class DelayedPermissionOwnerWorker(
        context: Context,
        params: WorkerParameters,
    ) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }
}

class CompletingPermissionOwnerWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result = Result.success()
}

class PersistablePermissionTestProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        val providerContext = checkNotNull(context)
        val uri = Uri.parse(checkNotNull(extras?.getString(TEST_PROVIDER_URI_EXTRA)))
        when (method) {
            "grant" -> providerContext.grantUriPermission(
                checkNotNull(arg),
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )

            "revoke" -> providerContext.revokeUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )

            else -> error("Unknown test provider method: $method")
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
