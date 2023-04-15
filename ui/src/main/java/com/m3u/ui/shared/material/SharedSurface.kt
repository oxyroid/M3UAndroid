package com.m3u.ui.shared.material

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.shared.SharedState
import com.m3u.ui.shared.sharedElement

@Composable
fun SharedSurface(
    state: SharedState,
    modifier: Modifier = Modifier,
    color: Color = SharedHostDefaults.surfaceColor(),
    contentColor: Color = SharedHostDefaults.surfaceContentColor(),
    content: @Composable () -> Unit,
) {
    Surface(
        color = color,
        contentColor = contentColor,
        elevation = state.elevation,
        shape = state.shape,
        modifier = Modifier
            .sharedElement(state)
            .then(modifier),
        content = content
    )
}

object SharedHostDefaults {
    @Composable
    fun surfaceColor(): Color = LocalTheme.current.background

    @Composable
    fun surfaceContentColor(): Color = LocalTheme.current.onBackground
}
