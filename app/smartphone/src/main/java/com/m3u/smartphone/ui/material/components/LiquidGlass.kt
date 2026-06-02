package com.m3u.smartphone.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(liquidGlassBackgroundBrush())
            .layerBackdrop(backdrop)
    ) {
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
            content()
        }
    }
}

@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    blurRadius: Float = 16f,
    refractionHeight: Float = 12f,
    refractionAmount: Float = 24f,
    chromaticAberration: Boolean = true
): Modifier {
    val backdrop = LocalLiquidGlassBackdrop.current ?: return this
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val actualSurfaceColor = surfaceColor.takeOrElse {
        if (isDark) colorScheme.surface.copy(alpha = 0.34f)
        else colorScheme.surface.copy(alpha = 0.46f)
    }
    val actualTint = tint.takeOrElse {
        colorScheme.primary.copy(alpha = if (isDark) 0.10f else 0.08f)
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.dp.toPx())
            lens(
                refractionHeight.dp.toPx(),
                refractionAmount.dp.toPx(),
                depthEffect = true,
                chromaticAberration = chromaticAberration
            )
        },
        highlight = {
            Highlight.Default.copy(alpha = if (isDark) 0.55f else 0.72f)
        },
        shadow = {
            Shadow(
                radius = 18.dp,
                color = Color.Black.copy(alpha = if (isDark) 0.22f else 0.12f)
            )
        },
        innerShadow = {
            InnerShadow(
                radius = 12.dp,
                color = if (isDark) Color.White.copy(alpha = 0.08f)
                else Color.Black.copy(alpha = 0.10f),
                alpha = 0.9f
            )
        },
        onDrawSurface = {
            drawRect(actualSurfaceColor)
            drawRect(actualTint)
        }
    )
}

@Composable
private fun liquidGlassBackgroundBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    return Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                colorScheme.background,
                colorScheme.primary.copy(alpha = 0.20f),
                colorScheme.surface
            )
        } else {
            listOf(
                colorScheme.background,
                colorScheme.primaryContainer.copy(alpha = 0.55f),
                colorScheme.tertiaryContainer.copy(alpha = 0.34f)
            )
        },
        start = Offset.Zero,
        end = Offset.Infinite
    )
}
