package com.m3u.material.ktx

import android.Manifest
import android.os.Build
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.shouldShowRationale

inline fun PermissionState.checkPermissionOrRationale(
    showRationale: () -> Unit = {},
    block: () -> Unit,
) {
    val sdk = Build.VERSION.SDK_INT
    val skip = when (permission) {
        Manifest.permission.POST_NOTIFICATIONS -> sdk < Build.VERSION_CODES.TIRAMISU

        // If you try to check or request the WRITE_EXTERNAL_STORAGE on Android 13+,
        // it will always return false.
        // So you'll have to skip the permission check/request completely on Android 13+.
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> sdk >= Build.VERSION_CODES.TIRAMISU
        else -> false
    }
    when {
        skip -> {}
        status is PermissionStatus.Denied -> {
            if (status.shouldShowRationale) {
                showRationale()
            } else {
                launchPermissionRequest()
            }
            return
        }

        else -> {}
    }
    block()
}