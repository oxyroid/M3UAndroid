package com.m3u.material.ktx

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
operator fun PaddingValues.plus(another: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        calculateStartPadding(direction) + another.calculateStartPadding(direction),
        calculateTopPadding() + another.calculateTopPadding(),
        calculateEndPadding(direction) + another.calculateEndPadding(direction),
        calculateBottomPadding() + another.calculateBottomPadding(),
    )
}

@Composable
operator fun PaddingValues.minus(another: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        calculateStartPadding(direction) - another.calculateStartPadding(direction),
        calculateTopPadding() - another.calculateTopPadding(),
        calculateEndPadding(direction) - another.calculateEndPadding(direction),
        calculateBottomPadding() - another.calculateBottomPadding(),
    )
}

@Composable
infix fun PaddingValues.only(side: WindowInsetsSides): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return when (side) {
        WindowInsetsSides.Start ->
            PaddingValues(start = calculateStartPadding(layoutDirection))

        WindowInsetsSides.End ->
            PaddingValues(end = calculateStartPadding(layoutDirection))

        WindowInsetsSides.Top -> PaddingValues(top = calculateTopPadding())
        WindowInsetsSides.Bottom -> PaddingValues(bottom = calculateBottomPadding())
        else -> this
    }
}

@Composable
infix fun PaddingValues.split(side: WindowInsetsSides?): Pair<PaddingValues, PaddingValues> {
    if (side == null) return PaddingValues() to this
    return (this only side) to (this - (this only side))
}