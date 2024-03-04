package com.m3u.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.toFontFamily

object FontFamilies {
    val GoogleSans = Font(R.font.google_sans).toFontFamily()
    val JetbrainsMono = Font(R.font.jb_mono_medium).toFontFamily()
    val LexendExa = Font(R.font.lexend_exa_medium).toFontFamily()
}

fun Typography.withFontFamily(
    fontFamily: FontFamily? = null
): Typography = copy(
    displayLarge = displayLarge.copy(
        fontFamily = fontFamily
    ),
    displayMedium = displayMedium.copy(
        fontFamily = fontFamily
    ),
    displaySmall = displaySmall.copy(
        fontFamily = fontFamily
    ),
    headlineLarge = headlineLarge.copy(
        fontFamily = fontFamily
    ),
    headlineMedium = headlineMedium.copy(
        fontFamily = fontFamily
    ),
    headlineSmall = headlineSmall.copy(
        fontFamily = fontFamily
    ),
    titleLarge = titleLarge.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = titleMedium.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold
    ),
    titleSmall = titleSmall.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium
    ),
    labelLarge = labelLarge.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold
    ),
    labelMedium = labelMedium.copy(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold
    ),
    labelSmall = labelSmall.copy(
        fontFamily = fontFamily
    ),
    bodyLarge = bodyLarge.copy(
        fontFamily = fontFamily
    ),
    bodyMedium = bodyMedium.copy(
        fontFamily = fontFamily
    ),
    bodySmall = bodySmall.copy(
        fontFamily = fontFamily
    )
)
