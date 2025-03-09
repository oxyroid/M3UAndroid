package com.m3u.material.model

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
import com.m3u.material.ktx.createScheme

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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        content()
    }
}
