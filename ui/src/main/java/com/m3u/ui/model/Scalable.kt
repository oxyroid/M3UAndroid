package com.m3u.ui.model

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit

data class Scalable(
    val value: Float
) {
    val Spacing.scaled: Spacing
        get() = copy(
            none = none * value,
            extraSmall = extraSmall * value,
            small = small * value,
            medium = medium * value,
            large = large * value,
            extraLarge = extraLarge * value,
            largest = largest * value
        )

    val TextUnit.scaled: TextUnit get() = this * (this@Scalable.value / 3f + 2 / 3f)
}

val LocalScalable = staticCompositionLocalOf {
    Scalable(1f)
}