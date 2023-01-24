package com.m3u.ui.local

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.m3u.ui.model.Duration

val LocalDuration: ProvidableCompositionLocal<Duration> = staticCompositionLocalOf {
    DurationDefaults.Default
}

object DurationDefaults {
    val Default = Duration(
        immediately = 0,
        fast = 200,
        medium = 600,
        slow = 800,
        extraSlow = 1200
    )
}