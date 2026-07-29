package com.m3u.smartphone.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.m3u.core.foundation.architecture.preferences.ThemePreset
import com.m3u.core.foundation.architecture.preferences.ThemeStyle
import com.m3u.smartphone.ui.material.ktx.createAppColorScheme
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloatingNavigationGlassTokensTest {
    @Test
    fun `light glass stays translucent while its boundary separates from app backgrounds`() {
        listOf(
            ThemePreset.DEFAULT_MATERIAL_SEED to ThemeStyle.MATERIAL,
            ThemePreset.WARM_EDITORIAL_SEED to ThemeStyle.WARM_EDITORIAL,
        ).forEach { (seed, style) ->
            val scheme = createAppColorScheme(
                argb = seed,
                isDark = false,
                themeStyle = style,
            )
            val tokens = resolveFloatingNavigationGlassTokens(
                colorScheme = scheme,
                useBackdropEffects = true,
            )
            val shell = tokens.surfaceColor.compositeOver(scheme.background)
            val boundary = tokens.outlineColor.compositeOver(shell)

            assertEquals(scheme.surfaceContainerLowest, tokens.surfaceColor.copy(alpha = 1f))
            assertEquals(0.40f, tokens.surfaceColor.alpha)
            assertEquals(0.75f, tokens.highlightAlpha)
            assertEquals(0.10f, tokens.shadowColor.alpha, absoluteTolerance = 0.005f)
            assertTrue(
                actual = contrastRatio(boundary, scheme.background) >= 2f,
                message = "$style floating navigation boundary is not distinct enough",
            )
        }
    }

    @Test
    fun `dark glass keeps a restrained container tint and stronger shadow`() {
        val scheme = createAppColorScheme(
            argb = ThemePreset.WARM_EDITORIAL_SEED,
            isDark = true,
            themeStyle = ThemeStyle.WARM_EDITORIAL,
        )
        val tokens = resolveFloatingNavigationGlassTokens(
            colorScheme = scheme,
            useBackdropEffects = true,
        )

        assertEquals(scheme.surfaceContainer, tokens.surfaceColor.copy(alpha = 1f))
        assertEquals(scheme.outlineVariant, tokens.outlineColor.copy(alpha = 1f))
        assertEquals(0.40f, tokens.surfaceColor.alpha)
        assertEquals(0.38f, tokens.highlightAlpha)
        assertEquals(0.20f, tokens.shadowColor.alpha, absoluteTolerance = 0.005f)
        assertEquals(Color.White, tokens.idleIndicatorColor.copy(alpha = 1f))
    }

    @Test
    fun `fallback glass is opaque enough without changing the shared chrome roles`() {
        val scheme = createAppColorScheme(
            argb = ThemePreset.DEFAULT_MATERIAL_SEED,
            isDark = false,
            themeStyle = ThemeStyle.MATERIAL,
        )
        val backdropTokens = resolveFloatingNavigationGlassTokens(
            colorScheme = scheme,
            useBackdropEffects = true,
        )
        val fallbackTokens = resolveFloatingNavigationGlassTokens(
            colorScheme = scheme,
            useBackdropEffects = false,
        )

        assertEquals(
            expected = 0.94f,
            actual = fallbackTokens.surfaceColor.alpha,
            absoluteTolerance = 0.005f,
        )
        assertEquals(backdropTokens.outlineColor, fallbackTokens.outlineColor)
        assertEquals(backdropTokens.shadowColor, fallbackTokens.shadowColor)
        assertEquals(backdropTokens.idleIndicatorColor, fallbackTokens.idleIndicatorColor)
    }
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (min(firstLuminance, secondLuminance) + 0.05f)
}
