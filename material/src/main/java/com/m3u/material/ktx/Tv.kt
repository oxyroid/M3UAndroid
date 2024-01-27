package com.m3u.material.ktx

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.m3u.material.model.asTvScheme
import com.m3u.material.model.asTvTypography

val LocalAlwaysTelevision = compositionLocalOf { false }

@Composable
fun isTelevision(): Boolean {
    val alwaysTelevision = LocalAlwaysTelevision.current
    return if (alwaysTelevision) true
    else LocalConfiguration.current.isTelevision()
}

fun Configuration.isTelevision(): Boolean {
    val type = uiMode and Configuration.UI_MODE_TYPE_MASK
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
