package com.m3u.smartphone.ui.navigation

import androidx.compose.material3.ColorScheme
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
    fun `glass keeps semantic theme roles and enough translucency for backdrop effects`() {
        testSchemes().forEach { schemeCase ->
            listOf(true, false).forEach { useBackdropEffects ->
                val tokens = resolveFloatingNavigationGlassTokens(
                    colorScheme = schemeCase.scheme,
                    useBackdropEffects = useBackdropEffects,
                )
                val expectedSurface = if (schemeCase.isDark) {
                    schemeCase.scheme.surfaceContainer
                } else {
                    schemeCase.scheme.surfaceContainerLowest
                }

                assertEquals(expectedSurface, tokens.surfaceColor.copy(alpha = 1f))
                assertEquals(
                    expected = if (useBackdropEffects) 0.78f else 0.94f,
                    actual = tokens.surfaceColor.alpha,
                    absoluteTolerance = 0.005f,
                )
                assertEquals(schemeCase.scheme.outline, tokens.outlineColor)
                assertEquals(
                    expected = if (schemeCase.isDark) Color.White else Color.Black,
                    actual = tokens.idleIndicatorColor.copy(alpha = 1f),
                )
                assertEquals(
                    expected = 0.06f,
                    actual = tokens.idleIndicatorColor.alpha,
                    absoluteTolerance = 0.005f,
                )
            }
        }
    }

    @Test
    fun `token composites keep a three to one baseline over solid scenes`() {
        testSchemes().forEach { schemeCase ->
            listOf(true, false).forEach { useBackdropEffects ->
                val tokens = resolveFloatingNavigationGlassTokens(
                    colorScheme = schemeCase.scheme,
                    useBackdropEffects = useBackdropEffects,
                )
                val scenes = listOf(
                    "black" to Color.Black,
                    "white" to Color.White,
                    "media-dark" to Color(0xFF121212),
                    "theme-background" to schemeCase.scheme.background,
                )

                scenes.forEach { (sceneName, scene) ->
                    // This is a token-level baseline before Haze blur, vibrancy, and
                    // refraction. Connected tests still exercise the rendered bar.
                    val shell = tokens.surfaceColor.compositeOver(scene)
                    val selectedSurface = tokens.idleIndicatorColor.compositeOver(shell)
                    val boundary = tokens.outlineColor.compositeOver(shell)
                    val contrasts = listOf(
                        "idle icon" to contrastRatio(
                            schemeCase.scheme.onSurfaceVariant,
                            shell,
                        ),
                        "selected icon" to contrastRatio(
                            schemeCase.scheme.primary,
                            selectedSurface,
                        ),
                        "accessory icon" to contrastRatio(
                            schemeCase.scheme.primary,
                            shell,
                        ),
                        "boundary" to contrastRatio(boundary, scene),
                    )

                    contrasts.forEach { (element, ratio) ->
                        assertTrue(
                            actual = ratio >= MINIMUM_MEANINGFUL_NON_TEXT_CONTRAST,
                            message = buildString {
                                append(schemeCase.name)
                                append(" ")
                                append(if (useBackdropEffects) "backdrop" else "fallback")
                                append(" ")
                                append(element)
                                append(" on ")
                                append(sceneName)
                                append(" has only ")
                                append(ratio)
                                append(":1 contrast")
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `light and dark glass retain their optical depth`() {
        testSchemes().forEach { schemeCase ->
            val tokens = resolveFloatingNavigationGlassTokens(
                colorScheme = schemeCase.scheme,
                useBackdropEffects = true,
            )

            assertEquals(
                expected = if (schemeCase.isDark) 0.38f else 0.75f,
                actual = tokens.highlightAlpha,
                absoluteTolerance = 0.005f,
            )
            assertEquals(
                expected = if (schemeCase.isDark) 0.20f else 0.10f,
                actual = tokens.shadowColor.alpha,
                absoluteTolerance = 0.005f,
            )
        }
    }
}

private data class TestScheme(
    val name: String,
    val isDark: Boolean,
    val scheme: ColorScheme,
)

private fun testSchemes(): List<TestScheme> = listOf(
    Triple("material-light", ThemePreset.DEFAULT_MATERIAL_SEED, ThemeStyle.MATERIAL),
    Triple("warm-light", ThemePreset.WARM_EDITORIAL_SEED, ThemeStyle.WARM_EDITORIAL),
).flatMap { (name, seed, style) ->
    listOf(false, true).map { isDark ->
        TestScheme(
            name = if (isDark) name.replace("-light", "-dark") else name,
            isDark = isDark,
            scheme = createAppColorScheme(
                argb = seed,
                isDark = isDark,
                themeStyle = style,
            ),
        )
    }
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (min(firstLuminance, secondLuminance) + 0.05f)
}

private const val MINIMUM_MEANINGFUL_NON_TEXT_CONTRAST = 3f
