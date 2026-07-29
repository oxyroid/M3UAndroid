package com.m3u.smartphone.ui.material.ktx

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.m3u.core.foundation.architecture.preferences.ThemePreset
import com.m3u.core.foundation.architecture.preferences.ThemeStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThemeColorSchemeTest {
    @Test
    fun `warm editorial theme uses paper and ink surfaces`() {
        val light = createAppColorScheme(
            argb = ThemePreset.WARM_EDITORIAL_SEED,
            isDark = false,
            themeStyle = ThemeStyle.WARM_EDITORIAL,
        )
        val dark = createAppColorScheme(
            argb = ThemePreset.WARM_EDITORIAL_SEED,
            isDark = true,
            themeStyle = ThemeStyle.WARM_EDITORIAL,
        )

        assertEquals(Color(0xFFFAF9F5), light.background)
        assertEquals(Color(0xFF141413), dark.background)
        assertNotEquals(light.surface, light.surfaceContainer)
        assertNotEquals(dark.surface, dark.surfaceContainer)
    }

    @Test
    fun `warm editorial semantic text pairs meet normal text contrast`() {
        listOf(false, true).forEach { isDark ->
            createAppColorScheme(
                argb = ThemePreset.WARM_EDITORIAL_SEED,
                isDark = isDark,
                themeStyle = ThemeStyle.WARM_EDITORIAL,
            ).assertSemanticContrast()
        }
    }

    @Test
    fun `material style keeps using the selected seed`() {
        val generic = createScheme(
            argb = ThemePreset.WARM_EDITORIAL_SEED,
            isDark = false,
        )
        val resolved = createAppColorScheme(
            argb = ThemePreset.WARM_EDITORIAL_SEED,
            isDark = false,
            themeStyle = ThemeStyle.MATERIAL,
        )

        assertEquals(generic.primary, resolved.primary)
        assertEquals(generic.secondary, resolved.secondary)
        assertEquals(generic.background, resolved.background)
        assertEquals(generic.surfaceContainer, resolved.surfaceContainer)
    }

    @Test
    fun `all app schemes provide contrast safe fixed color roles`() {
        listOf(ThemeStyle.MATERIAL, ThemeStyle.WARM_EDITORIAL).forEach { style ->
            listOf(
                ThemePreset.DEFAULT_MATERIAL_SEED,
                ThemePreset.WARM_EDITORIAL_SEED,
                0xCE5B73,
            ).forEach { seed ->
                listOf(false, true).forEach { isDark ->
                    createAppColorScheme(
                        argb = seed,
                        isDark = isDark,
                        themeStyle = style,
                    ).assertFixedRoles()
                }
            }
        }
    }

    @Test
    fun `raw color preview always chooses contrast safe black or white`() {
        listOf(
            Color.Black,
            Color.White,
            Color(0xFFD97757),
            Color(0xFF777777),
            Color(0xFF53653F),
        ).forEach { background ->
            val foreground = contrastingContentColor(background)
            assertTrue(contrastRatio(foreground, background) >= MINIMUM_NORMAL_TEXT_CONTRAST)
        }
    }
}

private fun ColorScheme.assertSemanticContrast() {
    val pairs = listOf(
        onPrimary to primary,
        onPrimaryContainer to primaryContainer,
        onSecondary to secondary,
        onSecondaryContainer to secondaryContainer,
        onTertiary to tertiary,
        onTertiaryContainer to tertiaryContainer,
        onBackground to background,
        onSurface to surface,
        onSurfaceVariant to surfaceVariant,
        inverseOnSurface to inverseSurface,
        onError to error,
        onErrorContainer to errorContainer,
    )

    pairs.forEach { (foreground, background) ->
        val contrast = contrastRatio(foreground, background)
        assertTrue(
            actual = contrast >= MINIMUM_NORMAL_TEXT_CONTRAST,
            message = "$foreground on $background has only $contrast:1 contrast",
        )
    }
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (min(firstLuminance, secondLuminance) + 0.05f)
}

private fun ColorScheme.assertFixedRoles() {
    val colors = listOf(
        primaryFixed,
        primaryFixedDim,
        onPrimaryFixed,
        onPrimaryFixedVariant,
        secondaryFixed,
        secondaryFixedDim,
        onSecondaryFixed,
        onSecondaryFixedVariant,
        tertiaryFixed,
        tertiaryFixedDim,
        onTertiaryFixed,
        onTertiaryFixedVariant,
    )
    colors.forEach { color ->
        assertNotEquals(Color.Unspecified, color)
    }

    val pairs = listOf(
        onPrimaryFixed to primaryFixed,
        onPrimaryFixed to primaryFixedDim,
        onPrimaryFixedVariant to primaryFixed,
        onPrimaryFixedVariant to primaryFixedDim,
        onSecondaryFixed to secondaryFixed,
        onSecondaryFixed to secondaryFixedDim,
        onSecondaryFixedVariant to secondaryFixed,
        onSecondaryFixedVariant to secondaryFixedDim,
        onTertiaryFixed to tertiaryFixed,
        onTertiaryFixed to tertiaryFixedDim,
        onTertiaryFixedVariant to tertiaryFixed,
        onTertiaryFixedVariant to tertiaryFixedDim,
    )
    pairs.forEach { (foreground, background) ->
        val contrast = contrastRatio(foreground, background)
        assertTrue(
            actual = contrast >= MINIMUM_NORMAL_TEXT_CONTRAST,
            message = "$foreground on $background has only $contrast:1 contrast",
        )
    }
    assertEquals(primary, surfaceTint)
}

private const val MINIMUM_NORMAL_TEXT_CONTRAST = 4.5f
