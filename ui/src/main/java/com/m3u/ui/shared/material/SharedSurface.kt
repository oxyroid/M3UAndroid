package com.m3u.ui.shared.material

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.shared.AnimatedModifier
import com.m3u.ui.shared.SharedScope

/**
 * A shared element surface.
 *
 * @param scope The current [SharedScope]
 * @param backgroundContent The root content.
 * @param foregroundContent The content which is both as shared element and font content.
 * @param onStart The callback when the [foregroundContent] should be started.
 * @see SharedScope
 * @suppress The [backgroundContent]'s lifecycle will not enter PAUSE or later state when it is dismiss.
 */
@Composable
fun SharedSurface(
    scope: SharedScope,
    backgroundContent: @Composable () -> Unit,
    foregroundContent: @Composable SharedScope.(Modifier) -> Unit,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    color: Color = SharedHostDefaults.surfaceColor(),
    contentColor: Color = SharedHostDefaults.surfaceContentColor(),
    elevation: Dp = SharedHostDefaults.surfaceElevation()
) {
    val currentOnStart by rememberUpdatedState(onStart)
    Surface(
        color = color,
        contentColor = contentColor
    ) {
        backgroundContent()
        if (scope.isElement) {
            AnimatedModifier(
                state = scope.state,
                elevation = elevation,
                modifier = modifier
            ) { modifier ->
                scope.foregroundContent(modifier)
            }
            LaunchedEffect(scope) {
                currentOnStart()
            }
        }
    }
}

object SharedHostDefaults {
    @Composable
    fun surfaceColor(): Color = LocalTheme.current.background

    @Composable
    fun surfaceContentColor(): Color = LocalTheme.current.onBackground

    @Composable
    fun surfaceElevation(): Dp = LocalSpacing.current.medium
}