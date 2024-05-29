package com.m3u.material.model

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.m3u.material.ktx.PlatformTheme
import com.m3u.material.ktx.createScheme
import androidx.tv.material3.ColorScheme as TvColorScheme
import androidx.tv.material3.Typography as TvTypography

@Composable
@SuppressLint("RestrictedApi")
fun Theme(
    argb: Int,
    useDynamicColors: Boolean,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    typography: Typography,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicTheming = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = remember(useDynamicColors, useDarkTheme, argb, context) {
        if (useDynamicColors && supportsDynamicTheming) {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            createScheme(argb, useDarkTheme)
        }
    }

    PlatformTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        content()
    }
}

fun ColorScheme.asTvScheme(): TvColorScheme {
    return TvColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceVariant, // todo
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        scrim = scrim,
        border = outline,
        borderVariant = outlineVariant
    )
}

fun Typography.asTvTypography(): TvTypography {
    return TvTypography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall
    )
}