package com.m3u.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.m3u.ui.R
import com.m3u.ui.model.Icon
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    text: String,
    visible: Boolean,
    windowInsets: WindowInsets = AppTopBarDefaults.windowInsets,
    onBackPressed: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val spacing = LocalSpacing.current

    val maxHeightDp = AppTopBarDefaults.TopBarHeight
    val minHeightDp = Dp.Hairline

    val maxHeightPx = with(density) { maxHeightDp.roundToPx().toFloat() }
    val minHeightPx = with(density) { minHeightDp.roundToPx().toFloat() }

    var offsetHeightPx by remember { mutableStateOf(maxHeightPx) }
    var minimize: Boolean by remember { mutableStateOf(false) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                offsetHeightPx = offsetHeightPx coercePlus available.y
                if (offsetHeightPx.isMinimize) minimize = true
                if (offsetHeightPx.isMaximize) minimize = false
                return if (offsetHeightPx.shouldBeConsumed) available
                else Offset.Zero
            }

            private infix fun Float.coercePlus(length: Float): Float =
                (length + this).coerceIn(0f, maxHeightPx)

            private val Float.isMinimize: Boolean get() = this == minHeightPx
            private val Float.isMaximize: Boolean get() = this == maxHeightPx
            private val Float.shouldBeConsumed: Boolean get() = (!this.isMinimize) && (!this.isMaximize)
        }
    }

    // Using Box instead of Column is because of making nestedScrollable components.
    Box(
        modifier = modifier
            .fillMaxSize()
            .let {
                if (visible) it.windowInsetsPadding(windowInsets) else it
            }
            .nestedScroll(
                connection = connection
            )
    ) {
        // [contentPaddingTop] is child content PaddingValue's top value,
        // it should be between 1~2 times [maxHeightDp].
        // Because the AppBar will place between 1~2 times [maxHeightDp].
        // The [visible] param means the AppBar should be invisible(0.dp) or not.
        val contentPaddingTop by remember(visible) {
            derivedStateOf {
                if (!visible) minHeightDp
                else maxHeightDp + with(density) {
                    offsetHeightPx.toDp()
                }
            }
        }

        // Child Content
        content(
            PaddingValues(
                start = spacing.none,
                top = contentPaddingTop,
                end = spacing.none,
                bottom = spacing.none
            )
        )

        // AppBar
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Crossfade(
                targetState = minimize,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium)
            ) { minimize ->
                if (minimize) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(contentPaddingTop),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.h6.copy(
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    val progress by remember { derivedStateOf { offsetHeightPx / maxHeightPx } }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(contentPaddingTop)
                            .graphicsLayer {
                                alpha = progress
                            },
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val scale by remember {
                            derivedStateOf {
                                AppTopBarDefaults.scaleInterpolator(
                                    slope = AppTopBarDefaults.ScaleSlope,
                                    input = progress
                                )
                            }
                        }
                        if (onBackPressed != null) {
                            // TODO: Refactor to Composable Param
                            IconButton(
                                icon = Icon.ImageVectorIcon(Icons.Rounded.ArrowBack),
                                contentDescription = stringResource(R.string.cd_top_bar_on_back_pressed),
                                onClick = onBackPressed
                            )
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.h5,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = spacing.small)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                        actions()
                    }
                }
            }
            if (visible) {
                Divider(color = LocalTheme.current.divider)
            }
        }
    }
}

internal object AppTopBarDefaults {
    val TopBarHeight = 52.dp
    const val ScaleSlope = 0.35f

    @OptIn(ExperimentalLayoutApi::class)
    val windowInsets: WindowInsets
        @Composable
        get() = WindowInsets.systemBarsIgnoringVisibility
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

    /**
     * Linear interpolator through point (1, 1).
     * @param slope the slope of the interpolator.
     * @param input the x value between 0~1f.
     */
    fun scaleInterpolator(slope: Float, input: Float): Float = lerp(input, 1f, 1 - slope)
}