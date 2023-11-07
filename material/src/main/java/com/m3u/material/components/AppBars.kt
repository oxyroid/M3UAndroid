package com.m3u.material.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.LocalAbsoluteElevation
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.m3u.material.ktx.animated
import com.m3u.material.model.LocalDuration
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.LocalTheme
import dev.chrisbanes.haze.haze

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Scaffold(
    title: String,
    visible: Boolean,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
    consumer: AppTopBarConsumer = AppTopBarDefaults.consumer,
    windowInsets: WindowInsets = AppTopBarDefaults.windowInsets,
    onBackPressed: (() -> Unit)? = null,
    onBackPressedContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.(PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val spacing = LocalSpacing.current
    val configuration = LocalConfiguration.current

    val maxHeightDp = AppTopBarDefaults.TopBarHeight
    val minHeightDp = Dp.Hairline

    val maxHeightPx = with(density) { maxHeightDp.roundToPx().toFloat() }
    val minHeightPx = with(density) { minHeightDp.roundToPx().toFloat() }

    var offsetHeightPx by remember { mutableFloatStateOf(maxHeightPx) }

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
                    if (innerConsumer) available else Offset.Zero
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
                .nestedScroll(
                    connection = connection
                )
        ) {
            // [contentPaddingTop] is child content PaddingValue's top value,
            // it should be between 1~2 times [maxHeightDp].
            // Because the AppBar will place between 1~2 times [maxHeightDp].
            // The [visible] param means the AppBar should be invisible(spacing.none) or not.
            val contentPaddingTop by remember(visible) {
                derivedStateOf {
                    if (!visible) minHeightDp
                    else with(density) {
                        offsetHeightPx.toDp()
                    }
                }
            }
            val direction = LocalLayoutDirection.current
            val actualBackgroundColor by LocalTheme.current.background.animated("AppBarBackground")
            val actualContentColor by LocalTheme.current.onBackground.animated("AppBarContent")
            // Child Content
            Box(
                modifier = Modifier
                    .let {
                        if (visible) it.haze(
                            area = with(density) {
                                arrayOf(
                                    Rect(
                                        0f,
                                        0f,
                                        configuration.screenWidthDp.dp.toPx(),
                                        WindowInsets.statusBarsIgnoringVisibility.getTop(density) + maxHeightPx
                                    )
                                )
                            },
                            backgroundColor = actualBackgroundColor,
                            blurRadius = 2.dp
                        )
                        else it
                    }

            ) {
                content(
                    PaddingValues(
                        start = spacing.none + if (!visible) spacing.none
                        else windowInsets.asPaddingValues().calculateStartPadding(direction),
                        top = contentPaddingTop + if (!visible) spacing.none
                        else windowInsets.asPaddingValues().calculateTopPadding(),
                        end = spacing.none + if (!visible) spacing.none
                        else windowInsets.asPaddingValues().calculateEndPadding(direction),
                        bottom = spacing.none + if (!visible) spacing.none
                        else windowInsets.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
            // AppBar
            val progress by remember { derivedStateOf { offsetHeightPx / maxHeightPx } }
            val duration = LocalDuration.current
            Surface(
                elevation = LocalAbsoluteElevation.current,
                color = Color.Transparent,
                contentColor = actualContentColor,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (visible) it.windowInsetsPadding(windowInsets)
                            else it
                        }
                        .padding(horizontal = spacing.medium)
                        .height(contentPaddingTop),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = onBackPressed != null,
                        enter = fadeIn(tween(delayMillis = duration.medium))
                                + expandHorizontally(tween(duration.medium)),
                        exit = fadeOut(tween(duration.medium))
                                + shrinkHorizontally(tween(delayMillis = duration.medium)),
                    ) {
                        IconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = onBackPressedContentDescription,
                            onClick = { if (progress > 0) onBackPressed?.invoke() },
                            modifier = Modifier.wrapContentSize()
                        )
                    }
                    Text(
                        text = title,
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
                    )
                    Row(
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
        @Composable get() = WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Top)

    /**
     * Linear interpolator through point (1, 1).
     * @param slope the slope of the interpolator.
     * @param input the x value between 0~1f.
     */
    fun interpolator(slope: Float, input: Float): Float = lerp(input, 1f, 1 - slope)
}

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
