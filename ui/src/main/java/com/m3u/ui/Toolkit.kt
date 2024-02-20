package com.m3u.ui

import android.view.inputmethod.BaseInputConnection
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.unspecified.unspecifiable
import com.m3u.data.service.RemoteDirectionService
import com.m3u.material.LocalM3UHapticFeedback
import com.m3u.material.createM3UHapticFeedback
import com.m3u.material.ktx.LocalAlwaysTelevision
import com.m3u.material.model.Theme
import com.m3u.ui.helper.Helper
import com.m3u.ui.helper.LocalHelper

@Composable
fun Toolkit(
    helper: Helper,
    pref: Pref,
    remoteDirectionService: RemoteDirectionService,
    alwaysUseDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val onBackPressedDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
    val prevTypography = MaterialTheme.typography
    val typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.GoogleSans)
    }

    val useDarkTheme = when {
        alwaysUseDarkTheme -> true
        pref.followSystemTheme -> isSystemInDarkTheme()
        else -> pref.darkMode
    }

    DisposableEffect(view) {
        val connection = BaseInputConnection(view, true)
        remoteDirectionService.init(connection) {
            onBackPressedDispatcher.onBackPressed()
        }
        onDispose {
            connection.closeConnection()
            remoteDirectionService.init(null, null)
        }
    }

    CompositionLocalProvider(
        LocalHelper provides helper,
        LocalPref provides pref,
        LocalAlwaysTelevision provides pref.alwaysTv,
        LocalM3UHapticFeedback provides createM3UHapticFeedback()
    ) {
        Theme(
            argb = pref.colorArgb,
            useDarkTheme = useDarkTheme,
            useDynamicColors = pref.useDynamicColors,
            typography = typography
        ) {
            LaunchedEffect(useDarkTheme) {
                helper.isSystemBarUseDarkMode = useDarkTheme.unspecifiable
            }
            content()
        }
    }
}
