package com.m3u.smartphone.ui.common.internal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
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
    val prevTypography = MaterialTheme.typography
    val smartphoneTypography: Material3Typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.GoogleSans)
    }
    val followSystemTheme by preferenceOf(PreferencesKeys.FOLLOW_SYSTEM_THEME)
    val darkMode by preferenceOf(PreferencesKeys.DARK_MODE)
    val compactDimension by preferenceOf(PreferencesKeys.COMPACT_DIMENSION)
    val argb by preferenceOf(PreferencesKeys.COLOR_ARGB)
    val useDynamicColors by preferenceOf(PreferencesKeys.USE_DYNAMIC_COLORS)

    val isSystemInDarkTheme = isSystemInDarkTheme()

    val useDarkTheme by remember {
        derivedStateOf {
            when {
                alwaysUseDarkTheme -> true
                followSystemTheme -> isSystemInDarkTheme
                else -> darkMode
            }
        }
    }

    val spacing = if (compactDimension) Spacing.COMPACT
    else Spacing.REGULAR
    CompositionLocalProvider(
        LocalHelper provides helper,
        LocalM3UHapticFeedback provides createM3UHapticFeedback(),
        LocalSpacing provides spacing
    ) {
        Theme(
            argb = argb,
            useDarkTheme = useDarkTheme,
            useDynamicColors = useDynamicColors,
            typography = smartphoneTypography
        ) {
            LaunchedEffect(useDarkTheme) {
                helper.isSystemBarUseDarkMode = useDarkTheme
            }
            content()
        }
    }
}
