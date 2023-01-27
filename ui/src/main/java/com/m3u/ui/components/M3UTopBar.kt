package com.m3u.ui.components

import androidx.compose.animation.Crossfade
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

@Composable
fun M3UTopBar(
    modifier: Modifier = Modifier,
    text: String,
    visible: Boolean,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val topBarHeight = 48.dp
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
            .nestedScroll(
                connection = connection
            )
    ) {
        val density = LocalDensity.current
        val paddingTopDp by remember {
            derivedStateOf {
                with(density) { offsetHeightPx.toDp() }
            }
        }
        val contentPaddingTop = if (!visible) 0.dp
        else topBarHeight + paddingTopDp

        content(
            PaddingValues(
                start = 0.dp,
                top = contentPaddingTop,
                end = 0.dp,
                bottom = 0.dp
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
                    .padding(horizontal = LocalSpacing.current.medium)
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
                        val k = 0.35f
                        val scale by remember {
                            derivedStateOf { k * process + (1 - k) }
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
                        actions()
                    }
                }
            }
            Divider()
        }
    }
}