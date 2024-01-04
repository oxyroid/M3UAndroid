package com.m3u.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref
import com.m3u.material.model.AppTheme

@Composable
fun M3ULocalProvider(
    helper: Helper = EmptyHelper,
    pref: Pref,
    content: @Composable () -> Unit
) {
    val prevTypography = MaterialTheme.typography
    val typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.GoogleSans)
    }
    CompositionLocalProvider(
        LocalHelper provides helper,
        LocalPref provides pref
    ) {
        AppTheme(
            useDarkTheme = pref.cinemaMode || isSystemInDarkTheme(),
            useDynamicColors = pref.useDynamicColors,
            typography = typography
        ) {
            content()
        }
    }
}
