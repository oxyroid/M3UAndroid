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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.ui.R
import com.m3u.ui.model.Icon
import com.m3u.ui.model.LocalSpacing

@Composable
fun M3UTopBar(
    modifier: Modifier = Modifier,
    text: String,
    visible: Boolean,
    windowInsets: WindowInsets = M3UTopBarDefaults.windowInsets,
    actions: @Composable RowScope.() -> Unit = {},
    onBackPressed: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val spacing = LocalSpacing.current

    val maxHeightDp = M3UTopBarDefaults.TopBarHeight
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
            .nestedScroll(
                connection = connection
            )
    ) {
        val paddingTopDp by remember {
            derivedStateOf {
                with(density) {
                    offsetHeightPx.toDp()
                }
            }
        }
        // [contentPaddingTop] is between 1~2 times [maxHeightDp].
        // Because the AppBar should place 1 time [maxHeightDp] at least.
        // The [visible] param means the AppBar should be invisible(0.dp) or not.
        val contentPaddingTop by remember(visible) {
            derivedStateOf {
                if (!visible) minHeightDp
                else maxHeightDp + paddingTopDp
            }
        }

        // Content
        content(
            PaddingValues(
                start = spacing.none,
                top = contentPaddingTop,
                end = spacing.none,
                bottom = spacing.none
            )
        )

        // AppBarContent
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
                            )
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
                                M3UTopBarDefaults.scaleInterpolator(
                                    curvature = M3UTopBarDefaults.ScaleCurvature,
                                    process = progress
                                )
                            }
                        }
                        if (onBackPressed != null) {
                            M3UIconButton(
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
            Divider()
        }
    }
}

internal object M3UTopBarDefaults {
    val TopBarHeight = 48.dp
    const val ScaleCurvature = 0.35f

    @OptIn(ExperimentalLayoutApi::class)
    val windowInsets: WindowInsets
        @Composable
        get() = WindowInsets.systemBarsIgnoringVisibility
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

    fun scaleInterpolator(curvature: Float, process: Float): Float {
        return curvature * (process - 1) + 1
    }
}