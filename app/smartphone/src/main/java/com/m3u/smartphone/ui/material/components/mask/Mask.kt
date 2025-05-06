package com.m3u.smartphone.ui.material.components.mask

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color

@Composable
fun Mask(
    state: MaskState,
    color: Color,
    modifier: Modifier = Modifier,
    control: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
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
        Box(
            modifier = Modifier
                .focusRequester(focusRequester)
                .drawBehind { drawRect(color) }
                .then(modifier)
        ) {
            Column(
                content = content
            )
            control()
        }
    }
}
