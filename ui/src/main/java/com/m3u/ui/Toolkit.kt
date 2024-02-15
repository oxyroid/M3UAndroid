package com.m3u.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref
import com.m3u.material.LocalM3UHapticFeedback
import com.m3u.material.createM3UHapticFeedback
import com.m3u.material.ktx.LocalAlwaysTelevision
import com.m3u.material.model.AppTheme
import com.m3u.ui.helper.EmptyHelper
import com.m3u.ui.helper.Helper
import com.m3u.ui.helper.LocalHelper
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun Toolkit(
    helper: Helper = EmptyHelper,
    pref: Pref,
    content: @Composable () -> Unit
) {
    val prevTypography = MaterialTheme.typography
    val typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.GoogleSans)
    }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(focusManager) {
        helper
            .remoteDirection
            .onEach { focusManager.moveFocus(it.asFocusDirection()) }
            .launchIn(this)
    }

    CompositionLocalProvider(
        LocalHelper provides helper,
        LocalPref provides pref,
        LocalAlwaysTelevision provides pref.alwaysTv,
        LocalM3UHapticFeedback provides createM3UHapticFeedback()
    ) {
        AppTheme(
            argb = pref.colorArgb,
            useDarkTheme = if (pref.followSystemTheme) isSystemInDarkTheme()
            else pref.darkMode,
            useDynamicColors = pref.useDynamicColors,
            typography = typography
        ) {
            content()
        }
    }
}
