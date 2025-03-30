package com.m3u.smartphone.ui.material.components.mask

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.m3u.smartphone.ui.material.components.Background

@Composable
fun Mask(
    state: MaskState,
    modifier: Modifier = Modifier,
    brush: Brush = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.54f),
        1f to Color.Black.copy(alpha = 0.54f)
    ),
    contentColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        Background(
            brush = brush,
            contentColor = contentColor
        ) {
            Box(
                modifier = modifier.focusRequester(focusRequester),
                content = content
            )
        }
    }
}
