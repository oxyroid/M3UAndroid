package com.m3u.smartphone.ui.common.internal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.smartphone.ui.common.helper.Helper
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.material.LocalM3UHapticFeedback
import com.m3u.smartphone.ui.material.components.FontFamilies
import com.m3u.smartphone.ui.material.components.withFontFamily
import com.m3u.smartphone.ui.material.createM3UHapticFeedback
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.model.Spacing
import com.m3u.smartphone.ui.material.model.Theme
import androidx.compose.material3.Typography as Material3Typography

@Composable
fun Toolkit(
    helper: Helper,
    alwaysUseDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val preferences = hiltPreferences()

    val prevTypography = MaterialTheme.typography
    val smartphoneTypography: Material3Typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.GoogleSans)
    }
    val useDarkTheme = when {
        alwaysUseDarkTheme -> true
        preferences.followSystemTheme -> isSystemInDarkTheme()
        else -> preferences.darkMode
    }

    val spacing = if (preferences.compactDimension) Spacing.COMPACT
    else Spacing.REGULAR
    CompositionLocalProvider(
        LocalHelper provides helper,
        LocalM3UHapticFeedback provides createM3UHapticFeedback(),
        LocalSpacing provides spacing
    ) {
        Theme(
            argb = preferences.argb,
            useDarkTheme = useDarkTheme,
            useDynamicColors = preferences.useDynamicColors,
            typography = smartphoneTypography
        ) {
            LaunchedEffect(useDarkTheme) {
                helper.isSystemBarUseDarkMode = useDarkTheme
            }
            content()
        }
    }
}
