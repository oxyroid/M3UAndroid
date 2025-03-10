package com.m3u.tv

import android.annotation.SuppressLint
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
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
        border = Color(scheme.outline),
        borderVariant = Color(scheme.outlineVariant),
        scrim = Color(scheme.scrim),
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
