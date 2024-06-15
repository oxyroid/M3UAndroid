package com.m3u.ui

import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.leanback.model.RemoteDirection
import com.m3u.data.leanback.model.keyCode
import com.m3u.material.LocalM3UHapticFeedback
import com.m3u.material.createM3UHapticFeedback
import com.m3u.material.model.Theme
import com.m3u.ui.helper.Helper
import com.m3u.ui.helper.LocalHelper
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun Toolkit(
    helper: Helper,
    alwaysUseDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val preferences = hiltPreferences()

    val onBackPressedDispatcher =
        checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
    val prevTypography = MaterialTheme.typography
    val smartphoneTypography: androidx.compose.material3.Typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.GoogleSans)
    }
    val useDarkTheme = when {
        alwaysUseDarkTheme -> true
        preferences.followSystemTheme -> isSystemInDarkTheme()
        else -> preferences.darkMode
    }

    val connection = remember(view) { BaseInputConnection(view, true) }

    EventHandler(Events.remoteDirection) { remoteDirection ->
        when (remoteDirection) {
            RemoteDirection.EXIT -> {
                onBackPressedDispatcher.onBackPressed()
            }

            else -> {
                connection.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, remoteDirection.keyCode)
                )
                // TODO
                delay(150.milliseconds)
                connection.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_UP, remoteDirection.keyCode)
                )
            }
        }
    }

    CompositionLocalProvider(
        LocalHelper provides helper,
        LocalM3UHapticFeedback provides createM3UHapticFeedback()
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
