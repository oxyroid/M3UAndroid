package com.m3u.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object FontFamilies {
    val Titillium = FontFamily(
        Font(R.font.titillium_web_regular),
        Font(R.font.titillium_web_italic, style = FontStyle.Italic),
        Font(R.font.titillium_web_medium, FontWeight.Medium),
        Font(R.font.titillium_web_medium_italic, FontWeight.Medium, style = FontStyle.Italic),
        Font(R.font.titillium_web_bold, FontWeight.Bold),
        Font(R.font.titillium_web_bold_italic, FontWeight.Bold, style = FontStyle.Italic),
    )
    val JetbrainsMono = FontFamily(
        Font(R.font.jb_mono_medium)
    )
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
        fontFamily = fontFamily
    ),
    titleMedium = titleMedium.copy(
        fontFamily = fontFamily
    ),
    titleSmall = titleSmall.copy(
        fontFamily = fontFamily
    ),
    labelLarge = labelLarge.copy(
        fontFamily = fontFamily
    ),
    labelMedium = labelMedium.copy(
        fontFamily = fontFamily
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
