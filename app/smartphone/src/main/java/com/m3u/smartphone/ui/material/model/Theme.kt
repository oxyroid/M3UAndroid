package com.m3u.smartphone.ui.material.model

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.m3u.core.foundation.architecture.preferences.ThemeStyle
import com.m3u.smartphone.ui.material.ktx.createAppColorScheme

val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle.MATERIAL }

@Composable
@SuppressLint("RestrictedApi")
fun Theme(
    argb: Int,
    themeStyle: Int,
    useDynamicColors: Boolean,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    typography: Typography,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicTheming = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (useDynamicColors && supportsDynamicTheming) {
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(useDarkTheme, argb, themeStyle) {
            createAppColorScheme(argb, useDarkTheme, themeStyle)
        }
    }
    val resolvedStyle = themeStyle.takeIf { it == ThemeStyle.WARM_EDITORIAL }
        ?: ThemeStyle.MATERIAL

    CompositionLocalProvider(
        LocalThemeStyle provides resolvedStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
