package com.m3u.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.ui.local.LocalSpacing

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun M3UTopBar(
    modifier: Modifier = Modifier,
    text: String,
    visible: Boolean,
    windowInsets: WindowInsets = M3UTopBarDefaults.windowInsets,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val topBarHeight = M3UTopBarDefaults.TopBarHeight
    val topBarHeightPx = with(LocalDensity.current) { topBarHeight.roundToPx().toFloat() }
    var offsetHeightPx by remember { mutableStateOf(topBarHeightPx) }
    var minimize: Boolean by remember {
        mutableStateOf(false)
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                offsetHeightPx = (available.y + offsetHeightPx).coerceIn(0f, topBarHeightPx)
                if (offsetHeightPx == 0f) {
                    minimize = true
                }
                if (offsetHeightPx == topBarHeightPx) {
                    minimize = false
                }
                return Offset.Zero
            }
        }
    }
    val process by remember {
        derivedStateOf { offsetHeightPx / topBarHeightPx }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
            .nestedScroll(
                connection = connection
            )
    ) {
        val density = LocalDensity.current
        val spacing = LocalSpacing.current
        val paddingTopDp by remember {
            derivedStateOf {
                with(density) {
                    offsetHeightPx.toDp()
                }
            }
        }
        val contentPaddingTop by remember(visible) {
            derivedStateOf {
                if (!visible) spacing.none
                else topBarHeight + paddingTopDp
            }
        }
        content(
            PaddingValues(
                start = spacing.none,
                top = contentPaddingTop,
                end = spacing.none,
                bottom = spacing.none
            )
        )
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Crossfade(
                targetState = minimize,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentPaddingTop)
                    .padding(horizontal = spacing.medium)
            ) { minimize ->
                if (minimize) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topBarHeight + paddingTopDp),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topBarHeight + paddingTopDp)
                            .graphicsLayer {
                                alpha = process
                            },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val scale by remember {
                            derivedStateOf {
                                M3UTopBarDefaults.scaleInterpolator(
                                    curvature = M3UTopBarDefaults.ScaleCurvature,
                                    process = process
                                )
                            }
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.h5,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedContent(
                            targetState = actions
                        ) {
                            it()
                        }
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