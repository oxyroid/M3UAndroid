package com.m3u.features.setting

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

@Immutable
internal data class UriWrapper(
    val uri: Uri? = null
)

@Composable
internal fun rememberUriWrapper(uri: Uri?): UriWrapper {
    return remember(uri) {
        UriWrapper(uri)
    }
}