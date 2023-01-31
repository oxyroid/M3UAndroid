package com.m3u.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.m3u.ui.local.DayTheme
import com.m3u.ui.local.LocalTheme
import com.m3u.ui.local.NightTheme
import com.m3u.ui.model.Background
import com.m3u.ui.model.LocalBackground
import com.m3u.ui.model.Typography

@Composable
fun M3ULocalProvider(content: @Composable () -> Unit) {
    val theme = if (isSystemInDarkTheme()) NightTheme
    else DayTheme
    val background = Background(
        color = theme.background
    )
    CompositionLocalProvider(
        LocalTheme provides theme,
        LocalBackground provides background
    ) {
        MaterialTheme(
            typography = Typography,
            content = content
        )
    }
}