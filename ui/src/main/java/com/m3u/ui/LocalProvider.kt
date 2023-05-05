package com.m3u.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.m3u.ui.model.DayTheme
import com.m3u.ui.model.EmptyHelper
import com.m3u.ui.model.Helper
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.NightTheme
import com.m3u.ui.model.Theme
import com.m3u.ui.model.Typography

@Composable
fun M3ULocalProvider(
    theme: Theme = if (isSystemInDarkTheme()) NightTheme
    else DayTheme,
    helper: Helper = EmptyHelper,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTheme provides theme,
        LocalHelper provides helper
    ) {
        MaterialTheme(
            typography = Typography,
            content = content
        )
    }
}