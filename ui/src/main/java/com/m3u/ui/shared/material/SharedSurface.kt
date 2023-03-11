package com.m3u.ui.shared.material

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.shared.SharedElementScope
import com.m3u.ui.shared.SharedScope
import com.m3u.ui.shared.SharedState
import com.m3u.ui.shared.animation.AnimatedModifier
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
@Deprecated("Use ShareSurface(state: SharedState,...) instead")
fun SharedSurface(
    scope: SharedScope,
    backgroundContent: @Composable () -> Unit,
    foregroundContent: @Composable SharedElementScope.() -> Unit,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    color: Color = SharedHostDefaults.surfaceColor(),
    contentColor: Color = SharedHostDefaults.surfaceContentColor(),
) {
    val currentOnStart by rememberUpdatedState(onStart)
    Surface(
        color = color,
        contentColor = contentColor
    ) {
        backgroundContent()
        if (scope is SharedElementScope) {
            AnimatedModifier(
                state = scope.state,
                modifier = modifier
            ) {
                foregroundContent(scope)
            }
            LaunchedEffect(scope) {
                currentOnStart()
            }
        }
    }
}
