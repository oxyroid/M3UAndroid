package com.m3u.ui.model

import androidx.compose.ui.graphics.Color

data class Theme(
    val name: String,
    val isDark: Boolean,
    val isDarkText: Boolean,
    val tint: Color,
    val onTint: Color,
    val tintDisable: Color,
    val onTintDisable: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val surface: Color,
    val onSurface: Color,
    val topBar: Color,
    val onTopBar: Color,
    val topBarDisable: Color,
    val onTopBarDisable: Color,
    val background: Color,
    val onBackground: Color,
    val secondaryBackground: Color,
    val pressed: Color,
    val onPressed: Color,
    val error: Color,
    val onError: Color,
    val divider: Color
)
