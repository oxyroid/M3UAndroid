package com.m3u.smartphone.ui.material.ktx

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

        // Android 10+ writes shared images through MediaStore without this legacy permission.
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> sdk >= Build.VERSION_CODES.Q
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
