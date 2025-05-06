package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.m3u.smartphone.ui.material.components.mask.Mask
import com.m3u.smartphone.ui.material.components.mask.MaskState
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Immutable
data class Paddings(
    val top: Dp = Dp.Unspecified,
    val bottom: Dp = Dp.Unspecified
)

@Composable
fun PlayerMask(
    state: MaskState,
    color: Color,
    modifier: Modifier = Modifier,
    header: @Composable RowScope.() -> Unit,
    body: @Composable RowScope.() -> Unit,
    footer: (@Composable RowScope.() -> Unit)? = null,
    slider: (@Composable () -> Unit)? = null,
    control: (@Composable BoxScope.() -> Unit) = {},
    onPaddingsChanged: (Paddings) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current
    val density = LocalDensity.current

    val windowInsets = WindowInsets.safeDrawing
    var size: Paddings by remember {
        mutableStateOf(
            Paddings(
                top = with(density) { windowInsets.getTop(density).toDp() },
                bottom = with(density) { windowInsets.getBottom(density).toDp() },
            )
        )
    }

    Mask(
        state = state,
        color = color,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        control = control
    ) {
        DisposableEffect(Unit) {
            onDispose {
                size = Paddings(
                    top = with(density) { windowInsets.getTop(density).toDp() },
                    bottom = with(density) { windowInsets.getBottom(density).toDp() }
                )
                onPaddingsChanged(size)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    size = size.copy(
                        top = with(density) { (it.size.height + windowInsets.getTop(density)).toDp() }
                    )
                    onPaddingsChanged(size)
                }
                .padding(horizontal = spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(
                spacing.none,
                Alignment.End
            ),
            verticalAlignment = Alignment.Top,
            content = header
        )
        val centerSpacing = remember(configuration.screenWidthDp, spacing) {
            (configuration.screenWidthDp / 6).dp.coerceAtLeast(spacing.medium)
        }
        Row(
            modifier = Modifier
                .padding(horizontal = spacing.medium)
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(
                centerSpacing,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically,
            content = body
        )
        Column(
            modifier = Modifier
                .systemGestureExclusion()
                .padding(horizontal = spacing.medium)
        ) {
            footer?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalAlignment = Alignment.Bottom,
                    content = it
                )
            }
            AnimatedVisibility(
                visible = slider != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.onGloballyPositioned {
                    size = size.copy(
                        bottom = with(density) { (it.size.height + windowInsets.getBottom(density)).toDp() }
                    )
                    onPaddingsChanged(size)
                }
            ) {
                slider?.invoke()
            }
        }
    }
}
