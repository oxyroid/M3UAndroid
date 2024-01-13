package com.m3u.material.ktx

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.m3u.material.model.asTvScheme
import com.m3u.material.model.asTvTypography

@Composable
fun isTelevision(): Boolean {
    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return type == Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun TelevisionChain(block: @Composable () -> Unit) {
    if (!isTelevision()) {
        block()
        return
    }
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    androidx.tv.material3.MaterialTheme(
        colorScheme = remember(scheme) { scheme.asTvScheme() },
        typography = remember(typography) { typography.asTvTypography() }
    ) {
        block()
    }
}

@Composable
inline fun <T> T.TelevisionChain(crossinline block: @Composable T.() -> Unit) {
    if (!isTelevision()) {
        block()
        return
    }
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    androidx.tv.material3.MaterialTheme(
        colorScheme = remember(scheme) { scheme.asTvScheme() },
        typography = remember(typography) { typography.asTvTypography() }
    ) {
        block()
    }
}