package com.m3u.core.foundation.architecture.preferences

/**
 * Stable values persisted for the visual treatment that accompanies a color theme.
 *
 * A theme style may change typography and semantic color construction, while the
 * selected seed color and light/dark preference remain independently configurable.
 */
object ThemeStyle {
    const val MATERIAL = 0
    const val WARM_EDITORIAL = 1
}

/**
 * Stable built-in theme identities. A preset is independent from user-created
 * seed-color records, so both may use the same seed without replacing each other.
 */
object ThemePreset {
    const val MATERIAL = "material"
    const val WARM_EDITORIAL = "warm-editorial"
    const val DEFAULT_MATERIAL_SEED = 0x5E6738
    const val WARM_EDITORIAL_SEED = 0xD97757
}

/**
 * Complete preference payload for one atomic theme change.
 */
data class ThemePreference(
    val presetId: String,
    val argb: Int,
    val isDark: Boolean,
    val style: Int,
) {
    companion object {
        val DEFAULT = ThemePreference(
            presetId = ThemePreset.MATERIAL,
            argb = ThemePreset.DEFAULT_MATERIAL_SEED,
            isDark = true,
            style = ThemeStyle.MATERIAL,
        )
    }
}

fun ThemePreference.normalized(): ThemePreference = when (presetId) {
    ThemePreset.WARM_EDITORIAL -> copy(
        argb = ThemePreset.WARM_EDITORIAL_SEED,
        style = ThemeStyle.WARM_EDITORIAL,
    )

    ThemePreset.MATERIAL -> copy(style = ThemeStyle.MATERIAL)
    else -> copy(
        presetId = ThemePreset.MATERIAL,
        style = ThemeStyle.MATERIAL,
    )
}

/**
 * One immutable view of every preference that can change the rendered theme.
 *
 * Reading these fields as one snapshot prevents a single theme selection from
 * briefly rendering a mixture of the old and new theme.
 */
data class ThemePreferencesSnapshot(
    val selection: ThemePreference,
    val useDynamicColors: Boolean,
    val followSystemTheme: Boolean,
) {
    companion object {
        val DEFAULT = ThemePreferencesSnapshot(
            selection = ThemePreference.DEFAULT,
            useDynamicColors = false,
            followSystemTheme = false,
        )
    }
}
