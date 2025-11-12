package com.m3u.smartphone.ui.material.model

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * Netflix-inspired design system with Apple glassmorphism
 */
object NetflixTheme {
    // Netflix-inspired colors
    val NetflixRed = Color(0xFFE50914)
    val NetflixBlack = Color(0xFF141414)
    val NetflixDarkGray = Color(0xFF1F1F1F)
    val NetflixGray = Color(0xFF2F2F2F)
    val NetflixLightGray = Color(0xFF808080)

    // Glass colors for glassmorphism
    val GlassLight = Color(0x40FFFFFF)
    val GlassMedium = Color(0x60FFFFFF)
    val GlassDark = Color(0x20FFFFFF)

    // Scrim gradients
    val TopScrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.9f),
        0.3f to Color.Black.copy(alpha = 0.6f),
        0.6f to Color.Transparent
    )

    val BottomScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.4f to Color.Black.copy(alpha = 0.4f),
        1f to Color.Black.copy(alpha = 0.95f)
    )

    val HeroScrim = Brush.verticalGradient(
        0f to Color.Transparent,
        0.7f to Color.Black.copy(alpha = 0.6f),
        1f to Color.Black.copy(alpha = 0.95f)
    )

    // Elevation values
    object Elevation {
        val Low = 2.dp
        val Medium = 4.dp
        val High = 8.dp
        val ExtraHigh = 16.dp
    }

    // Animation durations
    object Animation {
        const val Fast = 150
        const val Normal = 300
        const val Slow = 500
        const val VerySlow = 700
    }
}

/**
 * Glassmorphism modifier with blur and transparency
 */
fun Modifier.glassmorphism(
    backgroundColor: Color = NetflixTheme.GlassLight,
    blurRadius: Dp = 12.dp,
    shape: Shape = RectangleShape
): Modifier = composed {
    this
        .clip(shape)
        .background(backgroundColor, shape)
        .blur(blurRadius)
}

/**
 * Glassmorphism modifier using Haze for better performance
 */
fun Modifier.glassmorphismHaze(
    backgroundColor: Color = NetflixTheme.GlassLight,
    shape: Shape = RectangleShape,
    blurRadius: Dp = 20.dp,
    noiseFactor: Float = 0.15f
): Modifier = composed {
    val hazeState = LocalHazeState.current
    this
        .clip(shape)
        .background(backgroundColor, shape)
        .hazeChild(
            state = hazeState,
            style = HazeStyle(
                blurRadius = blurRadius,
                noiseFactor = noiseFactor,
                tint = backgroundColor.copy(alpha = 0.3f)
            ),
            shape = shape
        )
}

/**
 * Netflix-style card with hover effect
 */
fun Modifier.netflixCard(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    elevation: Dp = NetflixTheme.Elevation.Low
): Modifier = composed {
    this
        .clip(shape)
        .background(NetflixTheme.NetflixDarkGray, shape)
}

/**
 * Scrim overlay for images
 */
fun Modifier.scrim(
    brush: Brush = NetflixTheme.BottomScrim
): Modifier = this.background(brush)

/**
 * Animate content on appearance
 */
@Composable
fun rememberFadeInAnimation(
    durationMillis: Int = NetflixTheme.Animation.Normal
): Float {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis)
        )
    }

    return alpha.value
}
