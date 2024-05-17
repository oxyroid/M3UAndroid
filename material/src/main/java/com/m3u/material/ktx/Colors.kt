package com.m3u.material.ktx

import android.annotation.SuppressLint
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.Scheme

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
        surfaceTint = Color(scheme.surfaceVariant), // todo
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
        surfaceContainerLowest = getColor(argb, if (!isDark) 100 else 4, 6)
    )
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
