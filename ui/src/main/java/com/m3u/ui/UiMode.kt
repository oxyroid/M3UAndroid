package com.m3u.ui

import androidx.compose.runtime.Composable
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.material.ktx.isTelevision

enum class UiMode {
    DEFAULT, TELEVISION, COMPAT
}

@Composable
fun currentUiMode(): UiMode {
    if (isTelevision()) return UiMode.TELEVISION
    if (LocalPref.current.compact) return UiMode.COMPAT
    return UiMode.DEFAULT
}
