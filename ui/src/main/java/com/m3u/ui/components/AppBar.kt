package com.m3u.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
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
import com.m3u.ui.model.LocalDuration
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Suppress("unused")
interface AppTopBarConsumer {
    fun Float.value(min: Float, max: Float): Boolean

    object Never : AppTopBarConsumer {
        override fun Float.value(min: Float, max: Float): Boolean = false
    }

    object Edges : AppTopBarConsumer {
        override fun Float.value(min: Float, max: Float): Boolean = (this == min) || (this == max)
    }

    object Always : AppTopBarConsumer {
        override fun Float.value(min: Float, max: Float): Boolean = (this != min) && (this != max)
    }
}

fun AppTopBarConsumer.consume(value: Float, min: Float, max: Float): Boolean = value.value(min, max)

@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    text: String,
    visible: Boolean,
    scrollable: Boolean,
    consumer: AppTopBarConsumer = AppTopBarDefaults.consumer,
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

    LaunchedEffect(scrollable) {
        if (!scrollable) {
            offsetHeightPx = maxHeightPx
        }
    }

    val connection = remember(scrollable, consumer) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (scrollable) {
                    offsetHeightPx = offsetHeightPx coercePlus available.y
                    val innerConsumer = consumer.consume(offsetHeightPx, minHeightPx, maxHeightPx)
                    return if (innerConsumer) available else Offset.Zero
                } else Offset.Zero
            }

            private infix fun Float.coercePlus(length: Float): Float =
                (length + this).coerceIn(0f, maxHeightPx)
        }
    }

    CompositionLocalProvider(
        LocalContentColor provides LocalTheme.current.onTopBar
    ) {
        // Using Box instead of Column is because of making nestedScrollable components.
        Box(
            modifier = modifier
                .fillMaxSize()
                .let {
                    if (visible) it.windowInsetsPadding(windowInsets)
                    else it
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
                    else with(density) {
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
            val progress by remember { derivedStateOf { offsetHeightPx / maxHeightPx } }
            Surface(
                elevation = LocalAbsoluteElevation.current
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                        .height(contentPaddingTop),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val alpha by remember {
                        derivedStateOf {
                            progress * 2 - 1
                        }
                    }
                    val duration = LocalDuration.current

                    Box(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = tween(duration.medium)
                            )
                    ) {
                        if (onBackPressed != null) {
                            IconButton(
                                icon = Icon.ImageVectorIcon(Icons.Rounded.ArrowBack),
                                contentDescription = stringResource(R.string.cd_top_bar_on_back_pressed),
                                onClick = { if (progress > 0) onBackPressed() },
                                modifier = Modifier.graphicsLayer {
                                    this.alpha = alpha
                                }
                            )
                        }
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.h6.copy(
                            fontSize = 21.sp
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = spacing.extraSmall)
                            .graphicsLayer {
                                this.alpha = alpha
                            }
                    )
                    Row(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = tween(duration.medium)
                            )
                            .graphicsLayer {
                                this.alpha = alpha
                            },
                        content = actions
                    )
                }

            }
        }
    }
}

@Suppress("unused")
internal object AppTopBarDefaults {
    val TopBarHeight = 64.dp
    const val ScaleSlope = 0.35f
    val consumer = AppTopBarConsumer.Always

    @OptIn(ExperimentalLayoutApi::class)
    val windowInsets: WindowInsets
        @Composable get() = WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

    /**
     * Linear interpolator through point (1, 1).
     * @param slope the slope of the interpolator.
     * @param input the x value between 0~1f.
     */
    fun interpolator(slope: Float, input: Float): Float = lerp(input, 1f, 1 - slope)
}