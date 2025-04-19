package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.m3u.smartphone.ui.material.model.LocalDuration
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.components.Image

@Composable
internal fun CoverPlaceholder(
    visible: Boolean,
    cover: String,
    modifier: Modifier = Modifier
) {
    val duration = LocalDuration.current
    val spacing = LocalSpacing.current
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.65f,
            animationSpec = tween(duration.slow)
        ) + fadeIn(
            animationSpec = tween(duration.slow)
        ),
        exit = scaleOut(
            targetScale = 0.65f,
            animationSpec = tween(duration.slow)
        ) + fadeOut(
            animationSpec = tween(duration.slow)
        ),
        modifier = modifier.padding(spacing.medium)
    ) {
        Image(
            model = cover,
            modifier = Modifier.fillMaxSize(),
            transparentPlaceholder = true
        )
    }
}