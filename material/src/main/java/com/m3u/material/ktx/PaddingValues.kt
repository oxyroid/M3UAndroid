package com.m3u.material.ktx

import androidx.compose.foundation.layout.PaddingValues
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