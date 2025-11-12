package com.m3u.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api

/**
 * Netflix-inspired design system for Android TV
 * Optimized for 10-foot UI and remote control navigation
 */
object NetflixTvTheme {
    // Netflix-inspired colors
    val NetflixRed = Color(0xFFE50914)
    val NetflixBlack = Color(0xFF141414)
    val NetflixDarkGray = Color(0xFF1F1F1F)
    val NetflixGray = Color(0xFF2F2F2F)
    val NetflixLightGray = Color(0xFF808080)

    // Focus colors for TV
    val FocusedBorder = Color.White
    val FocusedBackground = Color.White.copy(alpha = 0.1f)

    // Glass colors (TV-optimized alpha values)
    val GlassLight = Color(0x30FFFFFF)
    val GlassMedium = Color(0x50FFFFFF)
    val GlassDark = Color(0x20FFFFFF)

    // Scrim gradients
    val HeroScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.5f to Color.Black.copy(alpha = 0.3f),
        0.8f to Color.Black.copy(alpha = 0.7f),
        1f to Color.Black.copy(alpha = 0.95f)
    )

    val CardScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.6f to Color.Black.copy(alpha = 0.4f),
        1f to Color.Black.copy(alpha = 0.9f)
    )

    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            NetflixBlack,
            NetflixDarkGray,
            NetflixBlack
        )
    )

    // TV-specific spacing (larger for 10-foot UI)
    object Spacing {
        val extraSmall = 8.dp
        val small = 16.dp
        val medium = 24.dp
        val large = 32.dp
        val extraLarge = 48.dp
        val huge = 64.dp
    }

    // TV-specific card sizes
    object CardSize {
        val width = 220.dp
        val height = 320.dp
        val wideWidth = 400.dp
        val wideHeight = 225.dp
    }

    // Focus border width for TV
    val FocusBorderWidth = 4.dp

    // Animation durations (TV-optimized - slightly slower)
    object Animation {
        const val Fast = 200
        const val Normal = 350
        const val Slow = 600
    }
}
