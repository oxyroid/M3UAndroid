package com.m3u.material.model

import android.annotation.SuppressLint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LightColors = lightColorScheme(
    primary = Color(0xFF855303),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF745A2E),
    onSecondary = Color(0xFFFFFFFF),
    surface = Color(0xFFFFF5DF),
    onSurface = Color(0xFF25190A),
    background = Color(0xFFFFF5DF),
    onBackground = Color(0xFF25190A),
    primaryContainer = Color(0xFFFFDDB8),
    onPrimaryContainer = Color(0xFF2A1700),
    tertiary = Color(0xFF725C1A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF91),
    onTertiaryContainer = Color(0xFF241A00),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inversePrimary = Color(0xFFFCBA66),
    inverseSurface = Color(0xFF3C2E1E),
    inverseOnSurface = Color(0xFFFFEEDE),
    outline = Color(0xFF86735F),
    outlineVariant = Color(0xFFD9C3AC),
    secondaryContainer = Color(0xFFFFDEAA),
    onSecondaryContainer = Color(0xFF271900),
    surfaceBright = Color(0xFFFFF5DF),
    surfaceTint = Color(0xFF855303),
    surfaceContainer = Color(0xFFFFEAD3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFEFD9),
    surfaceContainerHigh = Color(0xFFFAE4CE),
    surfaceContainerHighest = Color(0xFFF7DFC6),
    surfaceVariant = Color(0xFFF7DFC6),
    onSurfaceVariant = Color(0xFF544432),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFECD6C0)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFCBA66),
    onPrimary = Color(0xFF472A00),
    secondary = Color(0xFFE3C28C),
    onSecondary = Color(0xFF412D05),
    surface = Color(0xFF201000),
    onSurface = Color(0xFFF7DFC6),
    background = Color(0xFF201000),
    onBackground = Color(0xFFF7DFC6),
    primaryContainer = Color(0xFF653E00),
    onPrimaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFFE2C377),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF584401),
    onTertiaryContainer = Color(0xFFFFDF91),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inversePrimary = Color(0xFF855303),
    inverseSurface = Color(0xFFF7DFC6),
    inverseOnSurface = Color(0xFF3C2E1E),
    outline = Color(0xFFA18D78),
    outlineVariant = Color(0xFF544432),
    secondaryContainer = Color(0xFF5A4319),
    onSecondaryContainer = Color(0xFFFFDEAA),
    surfaceBright = Color(0xFF463624),
    surfaceTint = Color(0xFFFCBA66),
    surfaceContainer = Color(0xFF2B1D0A),
    surfaceContainerLowest = Color(0xFF1D0A00),
    surfaceContainerLow = Color(0xFF25190A),
    surfaceContainerHigh = Color(0xFF362716),
    surfaceContainerHighest = Color(0xFF413220),
    surfaceVariant = Color(0xFF544432),
    onSurfaceVariant = Color(0xFFD9C3AC),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF201000)
)

@Composable
@SuppressLint("NewApi")
fun AppTheme(
    useDynamicColors: Boolean,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    typography: Typography,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        useDarkTheme -> if (useDynamicColors) dynamicDarkColorScheme(context) else DarkColors
        else -> if (useDynamicColors) dynamicLightColorScheme(context) else LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
        typography = typography
    )
}