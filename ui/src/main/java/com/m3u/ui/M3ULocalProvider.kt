package com.m3u.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.m3u.material.components.Background
import com.m3u.material.model.DayTheme
import com.m3u.material.model.LocalTheme
import com.m3u.material.model.NightTheme
import com.m3u.material.model.Theme
import androidx.compose.material3.MaterialTheme as Material3Theme

@Composable
fun M3ULocalProvider(
    theme: Theme = if (isSystemInDarkTheme()) NightTheme else DayTheme,
    helper: Helper = EmptyHelper,
    content: @Composable () -> Unit
) {
    Material3Theme(
        colorScheme = Material3Theme.colorScheme.copy(
            surface = theme.surface,
            onSurface = theme.onSurface,
            background = theme.background,
            onBackground = theme.onBackground,
            primary = theme.tint,
            onPrimary = theme.onTint,
        )
    ) {
        MaterialTheme(
            typography = AppTypography,
            colors = MaterialTheme.colors.copy(
                surface = theme.surface,
                onSurface = theme.onSurface,
                background = theme.background,
                onBackground = theme.onBackground,
                primary = theme.tint,
                onPrimary = theme.onTint,
            )
        ) {
            CompositionLocalProvider(
                LocalTheme provides theme,
                LocalHelper provides helper
            ) {
                Background {
                    content()
                }
            }
        }
    }
}
