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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun Toolkit(
    helper: Helper,
    pref: Pref,
    actions: SharedFlow<RemoteDirectionService.Action>,
    alwaysUseDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val onBackPressedDispatcher =
        checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
    val prevTypography = MaterialTheme.typography
    val smartphoneTypography: androidx.compose.material3.Typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.GoogleSans)
    }
    val useDarkTheme = when {
        alwaysUseDarkTheme -> true
        pref.followSystemTheme -> isSystemInDarkTheme()
        else -> pref.darkMode
    }

    LaunchedEffect(view, actions) {
        val connection = BaseInputConnection(view, true)
        actions.collect { action ->
            when (action) {
                RemoteDirectionService.Action.Back -> {
                    onBackPressedDispatcher.onBackPressed()
                }

                is RemoteDirectionService.Action.Common -> {
                    connection.sendKeyEvent(
                        KeyEvent(KeyEvent.ACTION_DOWN, action.keyCode)
                    )
                    delay(150.milliseconds)
                    connection.sendKeyEvent(
                        KeyEvent(KeyEvent.ACTION_UP, action.keyCode)
                    )
                }
            }
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
            typography = smartphoneTypography,
        ) {
            LaunchedEffect(useDarkTheme) {
                helper.isSystemBarUseDarkMode = useDarkTheme.unspecifiable
            }
            content()
        }
    }
}
