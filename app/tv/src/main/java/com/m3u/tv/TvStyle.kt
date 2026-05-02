package com.m3u.tv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object TvColors {
    val Background = Color(0xFF05070B)
    val BackgroundSoft = Color(0xFF0D1118)
    val Surface = Color(0xFF151B24)
    val SurfaceRaised = Color(0xFF202A36)
    val Focus = Color(0xFF8DF2C7)
    val Accent = Color(0xFFFFC37A)
    val OnFocus = Color(0xFF06100C)
    val TextPrimary = Color(0xFFF7FAFC)
    val TextSecondary = Color(0xFFB6C1CC)
    val TextMuted = Color(0xFF7B8794)
}

object TvFonts {
    val Body = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semi_bold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )
    val Accent = FontFamily(
        Font(R.font.lexend_exa_medium, FontWeight.Medium)
    )
}

enum class TvDestination(
    val icon: ImageVector
) {
    Home(Icons.Rounded.Home),
    Library(Icons.AutoMirrored.Rounded.PlaylistPlay),
    Favorites(Icons.Rounded.Favorite),
    Status(Icons.Rounded.Settings)
}

enum class TvSurface {
    Browse,
    Player
}