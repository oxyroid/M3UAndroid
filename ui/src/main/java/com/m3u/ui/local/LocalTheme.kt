package com.m3u.ui.local

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.m3u.ui.model.Theme



val LocalTheme: ProvidableCompositionLocal<Theme> = staticCompositionLocalOf { DayTheme }

val DayTheme = Theme(
    name = "Day",
    isDark = false,
    isDarkText = true,
    tint = Color(0xff837fc9),
    onTint = Color(0xffffffff),
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

val NightTheme = Theme(
    name = "Night",
    isDark = true,
    isDarkText = false,
    tint = Color(0xff42b6fe),
    surface = Color(0xff232325),
    onSurface = Color(0xFFffffff),
    topBar = Color(0xff232325),
    onTopBar = Color(0xFFffffff),
    topBarDisable = Color(0xff232325),
    onTopBarDisable = Color(0xFFffffff),
    secondaryBackground = Color(0xff141622),
    secondary = Color(0xff202123),
    onSecondary = Color(0xffffffff),
    background = Color(0xff181818),
    onBackground = Color(0xffffffff),
    pressed = Color(0xff222222),
    onPressed = Color(0xff323232),
    tintDisable = Color(0xff8e8e8e),
    onTintDisable = Color(0xff8e8e8e),
    onTint = Color(0xffffffff),
    primary = Color(0xff387ab4),
    onPrimary = Color(0xffeef5f9),
    divider = Color(0xFF0A0A0A),
    error = Color(0xfff2b8b5),
    onError = Color(0xff601410)
)