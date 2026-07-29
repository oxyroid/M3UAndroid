package com.m3u.data.worker

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.work.Operation
import androidx.work.WorkManager
import java.security.MessageDigest

fun persistedUriPermissionTag(uri: Uri): String =
    hashedWorkTag(namespace = "persisted-uri", value = uri.toString())

internal fun hashedWorkTag(
    namespace: String,
    value: String,
): String = "$namespace:${value.sha256()}"

fun enqueuePersistedUriWork(
    workManager: WorkManager,
    permissionTag: String,
    enqueue: () -> Operation,
) {
    val operation = try {
        enqueue()
    } catch (error: Exception) {
        PersistedUriPermissionLeaseStore.finishPending(permissionTag)
        PersistedUriPermissionCleanupWorker.enqueue(
            workManager = workManager,
            permissionTag = permissionTag,
        )
        throw error
    }
    operation.result.addListener(
        {
            PersistedUriPermissionLeaseStore.finishPending(permissionTag)
            PersistedUriPermissionCleanupWorker.enqueue(
                workManager = workManager,
                permissionTag = permissionTag,
            )
        },
        { command -> command.run() },
    )
}

internal fun ContentResolver.releasePersistedPermission(uri: Uri) {
    val permission = persistedUriPermissions.firstOrNull { persisted ->
        persisted.uri == uri
    } ?: return
    var grantedFlags = 0
    if (permission.isReadPermission) {
        grantedFlags = grantedFlags or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    if (permission.isWritePermission) {
        grantedFlags = grantedFlags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
    if (grantedFlags != 0) {
        releasePersistableUriPermission(uri, grantedFlags)
    }
}

internal fun ContentResolver.persistedPermissionUriForTag(
    permissionTag: String,
): Uri? = persistedUriPermissions
    .firstOrNull { permission ->
        persistedUriPermissionTag(permission.uri) == permissionTag
    }
    ?.uri

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
