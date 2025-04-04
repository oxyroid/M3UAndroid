package com.m3u.smartphone.ui.business.foryou.components

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3u.business.foryou.R
import com.m3u.smartphone.ui.material.components.ProgressLottie

@Composable
internal fun Loading(
    @FloatRange(0.0, 1.0) offset: Float,
    modifier: Modifier = Modifier
) {
    ProgressLottie(
        raw = R.raw.loading,
        progress = { offset },
        modifier = modifier
    )
}