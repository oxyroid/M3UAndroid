package com.m3u.feature.channel.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.m3u.material.model.LocalDuration
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Image

@Composable
internal fun CoverPlaceholder(
    visible: Boolean,
    cover: String,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val duration = LocalDuration.current
    val spacing = LocalSpacing.current
    val size = remember(configuration.screenHeightDp) {
        configuration.screenHeightDp.dp
    }
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
            modifier = Modifier.size(size),
            transparentPlaceholder = true
        )
    }
}