package com.m3u.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.m3u.material.components.Background
import com.m3u.material.model.AppTheme

@Composable
fun M3ULocalProvider(
    colorScheme: ColorScheme = if (!isSystemInDarkTheme()) dynamicLightColorScheme(LocalContext.current)
    else dynamicDarkColorScheme(LocalContext.current),
    helper: Helper = EmptyHelper,
    content: @Composable () -> Unit
) {
    val prevTypography = MaterialTheme.typography
    val typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.Titillium)
    }
    AppTheme(
        typography = typography
    ) {
//    MaterialTheme(
//        typography = typography,
//    ) {
        CompositionLocalProvider(
            LocalHelper provides helper
        ) {
            Background {
                content()
            }
        }
    }
}
