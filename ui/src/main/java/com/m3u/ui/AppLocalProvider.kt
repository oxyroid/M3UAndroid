package com.m3u.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref
import com.m3u.material.model.AppTheme
import com.m3u.ui.helper.EmptyHelper
import com.m3u.ui.helper.Helper
import com.m3u.ui.helper.LocalHelper

@Composable
fun AppLocalProvider(
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
            argb = pref.colorArgb,
            useDarkTheme = pref.darkMode,
            useDynamicColors = pref.useDynamicColors,
            typography = typography
        ) {
            content()
        }
    }
}
