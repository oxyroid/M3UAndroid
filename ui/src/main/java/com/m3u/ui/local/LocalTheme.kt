package com.m3u.ui.local

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.m3u.ui.model.Theme



val LocalTheme: ProvidableCompositionLocal<Theme> = staticCompositionLocalOf { PresetTheme }

private val PresetTheme = Theme(
    name = "Preset-Theme",
    isDark = false,
    isDarkText = true,
    tint = Color(0xff837fc9),
    onTint = Color(0xffeef7fb),
    tintDisable = Color(0xffc7c6cb),
    onTintDisable = Color(0xfff6f5f9),
    primary = Color(0xff5a91de),
    onPrimary = Color(0xffffffff),
    secondary = Color(0xffc7c6cb),
    onSecondary = Color(0xff000000),
    surface = Color(0xFFeeeeee),
    onSurface = Color(0xff000000),
    topBar = Color(0xFFeeeeee),
    onTopBar = Color(0xFF191C1B),
    topBarDisable = Color(0xff837fc9),
    onTopBarDisable = Color(0xffeef7fb),
    background = Color(0xfffefefe),
    onBackground = Color(0xff2a2a2a),
    secondaryBackground = Color(0xff7eb2a8),
    pressed = Color(0xfff8f8f8),
    onPressed = Color(0xff323232),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    divider = Color(0xFFf0f0f0)
)