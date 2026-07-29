package com.m3u.smartphone.ui.material.ktx

import android.annotation.SuppressLint
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.Scheme
import com.m3u.core.foundation.architecture.preferences.ThemePreset
import com.m3u.core.foundation.architecture.preferences.ThemeStyle

fun createAppColorScheme(
    argb: Int,
    isDark: Boolean,
    themeStyle: Int,
): ColorScheme = when (themeStyle) {
    ThemeStyle.WARM_EDITORIAL -> createWarmEditorialScheme(isDark)
    else -> createScheme(argb, isDark)
}

@SuppressLint("RestrictedApi")
fun createScheme(
    argb: Int,
    isDark: Boolean
): ColorScheme {
    val scheme = if (isDark) Scheme.dark(argb)
    else Scheme.light(argb)
    return ColorScheme(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        inversePrimary = Color(scheme.inversePrimary),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
        surface = Color(scheme.surface),
        onSurface = Color(scheme.onSurface),
        surfaceVariant = Color(scheme.surfaceVariant),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),
        surfaceTint = Color(scheme.primary),
        inverseSurface = Color(scheme.inverseSurface),
        inverseOnSurface = Color(scheme.inverseOnSurface),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),
        scrim = Color(scheme.scrim),
        /**
         * Color Role                   Tone: Light	    Tone: Dark
         * Surface Dim                  N-87	        N-6
         * Surface Bright               N-98	        N-24
         * Surface Container  Lowest    N-100	        N-4
         * Surface Container  Low       N-96	        N-10
         * Surface Container            N-94	        N-12
         * Surface Container  High      N-92	        N-17
         * Surface Container  Highest	N-90	        N-22
         */
        surfaceBright = getColor(argb, if (!isDark) 98 else 24, 6),
        surfaceDim = getColor(argb, if (!isDark) 87 else 6, 6),
        surfaceContainer = getColor(argb, if (!isDark) 94 else 12, 6),
        surfaceContainerHigh = getColor(argb, if (!isDark) 92 else 17, 6),
        surfaceContainerHighest = getColor(argb, if (!isDark) 90 else 22, 6),
        surfaceContainerLow = getColor(argb, if (!isDark) 96 else 10, 6),
        surfaceContainerLowest = getColor(argb, if (!isDark) 100 else 4, 6),
        primaryFixed = Color(getColor(scheme.primary, 90)),
        primaryFixedDim = Color(getColor(scheme.primary, 80)),
        onPrimaryFixed = Color(getColor(scheme.primary, 10)),
        onPrimaryFixedVariant = Color(getColor(scheme.primary, 30)),
        secondaryFixed = Color(getColor(scheme.secondary, 90)),
        secondaryFixedDim = Color(getColor(scheme.secondary, 80)),
        onSecondaryFixed = Color(getColor(scheme.secondary, 10)),
        onSecondaryFixedVariant = Color(getColor(scheme.secondary, 30)),
        tertiaryFixed = Color(getColor(scheme.tertiary, 90)),
        tertiaryFixedDim = Color(getColor(scheme.tertiary, 80)),
        onTertiaryFixed = Color(getColor(scheme.tertiary, 10)),
        onTertiaryFixedVariant = Color(getColor(scheme.tertiary, 30)),
    )
}

/**
 * A warm editorial palette inspired by paper, ink, and restrained print accents.
 *
 * These are semantic Material color roles, not a direct copy of another product's
 * brand tokens. Text/container pairs are deliberately kept above WCAG AA contrast.
 */
private fun createWarmEditorialScheme(isDark: Boolean): ColorScheme {
    val base = createScheme(ThemePreset.WARM_EDITORIAL_SEED, isDark)
    val scheme = if (isDark) {
        base.copy(
            primary = Color(0xFFF0A58D),
            onPrimary = Color(0xFF4E1406),
            primaryContainer = Color(0xFF70301C),
            onPrimaryContainer = Color(0xFFFFDAD0),
            inversePrimary = Color(0xFF9B422B),
            secondary = Color(0xFFB8CEA0),
            onSecondary = Color(0xFF263417),
            secondaryContainer = Color(0xFF3C4C2A),
            onSecondaryContainer = Color(0xFFDCE8C9),
            tertiary = Color(0xFFA5CBE4),
            onTertiary = Color(0xFF0C344A),
            tertiaryContainer = Color(0xFF264D63),
            onTertiaryContainer = Color(0xFFCDE5F7),
            background = Color(0xFF141413),
            onBackground = Color(0xFFF2F0E8),
            surface = Color(0xFF141413),
            onSurface = Color(0xFFF2F0E8),
            surfaceVariant = Color(0xFF474640),
            onSurfaceVariant = Color(0xFFC9C6BD),
            surfaceTint = Color(0xFFF0A58D),
            inverseSurface = Color(0xFFE6E3DA),
            inverseOnSurface = Color(0xFF2F2E2A),
            outline = Color(0xFF938F85),
            outlineVariant = Color(0xFF474640),
            surfaceBright = Color(0xFF3A3934),
            surfaceDim = Color(0xFF141413),
            surfaceContainerLowest = Color(0xFF10100F),
            surfaceContainerLow = Color(0xFF1B1B19),
            surfaceContainer = Color(0xFF201F1D),
            surfaceContainerHigh = Color(0xFF2A2926),
            surfaceContainerHighest = Color(0xFF34332F),
        )
    } else {
        base.copy(
            primary = Color(0xFF9B422B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF4D4C8),
            onPrimaryContainer = Color(0xFF351008),
            inversePrimary = Color(0xFFF0A58D),
            secondary = Color(0xFF53653F),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDCE8C9),
            onSecondaryContainer = Color(0xFF152109),
            tertiary = Color(0xFF3F6784),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFCDE5F7),
            onTertiaryContainer = Color(0xFF071E2C),
            background = Color(0xFFFAF9F5),
            onBackground = Color(0xFF1F1F1C),
            surface = Color(0xFFFAF9F5),
            onSurface = Color(0xFF1F1F1C),
            surfaceVariant = Color(0xFFE8E6DC),
            onSurfaceVariant = Color(0xFF4B4942),
            surfaceTint = Color(0xFF9B422B),
            inverseSurface = Color(0xFF31302C),
            inverseOnSurface = Color(0xFFF3F1EA),
            outline = Color(0xFF79776F),
            outlineVariant = Color(0xFFCBC9C0),
            surfaceBright = Color(0xFFFAF9F5),
            surfaceDim = Color(0xFFDDDCD4),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF5F3EC),
            surfaceContainer = Color(0xFFEFEEE7),
            surfaceContainerHigh = Color(0xFFE9E7DF),
            surfaceContainerHighest = Color(0xFFE3E1D9),
        )
    }
    return scheme.copy(
        primaryFixed = Color(0xFFF4D4C8),
        primaryFixedDim = Color(0xFFF0A58D),
        onPrimaryFixed = Color(0xFF351008),
        onPrimaryFixedVariant = Color(0xFF70301C),
        secondaryFixed = Color(0xFFDCE8C9),
        secondaryFixedDim = Color(0xFFB8CEA0),
        onSecondaryFixed = Color(0xFF152109),
        onSecondaryFixedVariant = Color(0xFF3C4C2A),
        tertiaryFixed = Color(0xFFCDE5F7),
        tertiaryFixedDim = Color(0xFFA5CBE4),
        onTertiaryFixed = Color(0xFF071E2C),
        onTertiaryFixedVariant = Color(0xFF264D63),
    )
}

internal fun contrastingContentColor(background: Color): Color =
    if (background.luminance() > DARK_CONTENT_LUMINANCE_THRESHOLD) {
        Color.Black
    } else {
        Color.White
    }

@ColorInt
@SuppressLint("RestrictedApi")
private fun getColor(
    @ColorInt color: Int,
    @IntRange(from = 0L, to = 100L) tone: Int
): Int {
    val hctColor = Hct.fromInt(color)
    hctColor.tone = tone.toDouble()
    return hctColor.toInt()
}

@ColorInt
@SuppressLint("RestrictedApi")
private fun getColor(
    @ColorInt color: Int,
    @IntRange(from = 0L, to = 100L) tone: Int,
    chroma: Int
): Color {
    val hctColor = Hct.fromInt(getColor(color, tone))
    hctColor.chroma = chroma.toDouble()
    return Color(hctColor.toInt())
}

private const val DARK_CONTENT_LUMINANCE_THRESHOLD = 0.179f
