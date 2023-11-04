package com.m3u.ui

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppFont {
    val TitilliumWeb = FontFamily(
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

val AppTypography = Typography(
    defaultFontFamily = AppFont.TitilliumWeb,
    h1 = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = AppFont.TitilliumWeb,
        lineHeight = 27.sp
    ),
    h2 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = AppFont.TitilliumWeb,
        lineHeight = 24.sp,
    ),
    body1 = TextStyle(
        fontSize = 16.sp,
        fontFamily = AppFont.TitilliumWeb,
    ),
    body2 = TextStyle(
        fontSize = 16.sp,
        fontFamily = AppFont.TitilliumWeb,
    ),
    button = TextStyle(
        fontFamily = AppFont.TitilliumWeb,
        fontWeight = FontWeight.Medium,
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    subtitle1 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
    )
)
