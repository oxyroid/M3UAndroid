package com.m3u.material.components.mask

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.material.components.Background
import com.m3u.material.components.OuterBox

@Composable
fun Mask(
    state: MaskState,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentColor: Color = LocalContentColor.current,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Background(color = color, contentColor = contentColor) {
            OuterBox(
                modifier = modifier,
                content = content
            )
        }
    }
}
