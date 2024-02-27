package com.m3u.ui

import androidx.compose.runtime.Composable
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.material.ktx.isTelevision

enum class UiMode {
    Default, Television, Compat
}

@Composable
fun currentUiMode(): UiMode = when {
    isTelevision() -> UiMode.Television
    LocalPref.current.compact -> UiMode.Compat
    else -> UiMode.Default
}
