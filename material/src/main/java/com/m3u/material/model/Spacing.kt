package com.m3u.material.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val largest: Dp = 40.dp
) {
    companion object {
        val REGULAR = Spacing(
            none = 0.dp,
            extraSmall = 4.dp,
            small = 8.dp,
            medium = 16.dp,
            large = 24.dp,
            extraLarge = 32.dp,
            largest = 40.dp
        )
        val COMPACT = Spacing(
            none = 0.dp,
            extraSmall = 2.dp,
            small = 6.dp,
            medium = 12.dp,
            large = 16.dp,
            extraLarge = 24.dp,
            largest = 32.dp
        )
    }
}

val LocalSpacing = compositionLocalOf { Spacing.COMPACT }
