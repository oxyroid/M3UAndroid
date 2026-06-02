package com.m3u.smartphone.ui.material.model

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.m3u.smartphone.ui.material.ktx.createScheme

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

    val liquidColorScheme = remember(colorScheme) {
        colorScheme.copy(
            background = Color.Transparent,
            surface = colorScheme.surface.copy(alpha = 0.78f),
            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = 0.56f),
            surfaceContainer = colorScheme.surfaceContainer.copy(alpha = 0.46f),
            surfaceContainerLow = colorScheme.surfaceContainerLow.copy(alpha = 0.36f),
            surfaceContainerHigh = colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
            surfaceContainerHighest = colorScheme.surfaceContainerHighest.copy(alpha = 0.68f)
        )
    }

    MaterialTheme(
        colorScheme = liquidColorScheme,
        typography = typography
    ) {
        content()
    }
}
