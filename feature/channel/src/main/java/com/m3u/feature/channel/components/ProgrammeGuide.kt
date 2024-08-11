package com.m3u.feature.channel.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.paging.compose.LazyPagingItems
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.ProgrammeRange.Companion.HOUR_LENGTH
import com.m3u.material.components.Icon
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdges
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.FontFamilies
import com.m3u.ui.util.TimeUtils.formatEOrSh
import com.m3u.ui.util.TimeUtils.toEOrSh
import eu.wewox.minabox.MinaBox
import eu.wewox.minabox.MinaBoxItem
import eu.wewox.minabox.MinaBoxScrollDirection
import eu.wewox.minabox.MinaBoxState
import eu.wewox.minabox.rememberMinaBoxState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import androidx.tv.material3.ClickableSurfaceDefaults as TvClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface

private enum class Zoom(val time: Float) {
    DEFAULT(1f), ZOOM_1_5(1.5f), ZOOM_2(2f), ZOOM_5(5f)
}

@Composable
internal fun ProgramGuide(
    isPanelExpanded: Boolean,
    programmes: LazyPagingItems<Programme>,
    range: ProgrammeRange,
    programmeReminderIds: List<Int>,
    modifier: Modifier = Modifier,
    minaBoxState: MinaBoxState = rememberMinaBoxState(),
    height: Float = 256f,
    padding: Float = 16f,
    currentTimelineHeight: Float = 48f,
    scrollOffset: Int = -120,
    onProgrammePressed: (Programme) -> Unit
) {
    val spacing = LocalSpacing.current
    val tv = tv()

    val currentMilliseconds by produceCurrentMillisecondState()
    val coroutineScope = rememberCoroutineScope()

    var zoom: Zoom by remember { mutableStateOf(Zoom.DEFAULT) }
    val zoomGestureModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                zoom = when (zoom) {
                    Zoom.DEFAULT -> Zoom.ZOOM_1_5
                    Zoom.ZOOM_1_5 -> Zoom.ZOOM_2
                    Zoom.ZOOM_2 -> Zoom.ZOOM_5
                    Zoom.ZOOM_5 -> Zoom.DEFAULT
                }
            }
        )
    }
    val currentHeight: Float by animateFloatAsState(
        targetValue = height * zoom.time,
        label = "minabox-cell-height"
    )

    val currentTimelineOffset by remember(range.start) {
        derivedStateOf {
            (currentMilliseconds - range.start).toFloat() / HOUR_LENGTH * currentHeight
        }
    }

    val animateToCurrentTimeline: suspend () -> Unit by rememberUpdatedState {
        minaBoxState.animateTo(
            0f,
            currentTimelineOffset + scrollOffset
        )
    }

    if (isPanelExpanded) {
        LaunchedEffect(Unit) {
            animateToCurrentTimeline()
        }
    }

    BoxWithConstraints {
        MinaBox(
            state = minaBoxState,
            scrollDirection = MinaBoxScrollDirection.VERTICAL,
            modifier = Modifier
                .fillMaxSize()
                .blurEdges(
                    MaterialTheme.colorScheme.surface,
                    listOf(Edge.Top, Edge.Bottom)
                )
                .thenIf(!tv) { zoomGestureModifier }
                .then(modifier)
        ) {
            // programmes
            items(
                count = programmes.itemCount,
                layoutInfo = { index ->
                    val programme = programmes[index]
                    if (programme != null) {
                        val start = programme.start
                        val end = programme.end
                        MinaBoxItem(
                            x = padding,
                            y = currentHeight * (start - range.start) / HOUR_LENGTH + padding * 3,
                            width = (constraints.maxWidth - padding * 2)
                                .coerceAtLeast(0f),
                            height = (currentHeight * (end - start) / HOUR_LENGTH - padding)
                                .coerceAtLeast(0f)
                        )
                    } else {
                        MinaBoxItem(0f, 0f, 0f, 0f)
                    }
                }
            ) { index ->
                val programme = programmes[index]
                if (programme != null) {
                    ProgrammeCell(
                        programme = programme,
                        inReminder = programme.id in programmeReminderIds,
                        onPressed = { onProgrammePressed(programme) }
                    )
                } else {
                    // Placeholder
                }
            }

            // current timeline
            items(
                count = 1,
                layoutInfo = {
                    MinaBoxItem(
                        x = 0f,
                        y = currentTimelineOffset + padding * 2,
                        width = constraints.maxWidth.toFloat(),
                        height = currentTimelineHeight
                    )
                }
            ) {
                CurrentTimelineCell(
                    milliseconds = currentMilliseconds
                )
            }

            // background
            items(
                count = 1,
                layoutInfo = {
                    MinaBoxItem(
                        x = 0f,
                        y = 0f,
                        width = constraints.maxWidth.toFloat(),
                        height = with(range) {
                            (currentHeight * (end - start) / HOUR_LENGTH - padding)
                        }
                    )
                }
            ) {
            }
        }

        if (!tv) {
            Controls(
                animateToCurrentTimeline = {
                    coroutineScope.launch { animateToCurrentTimeline() }
                },
                modifier = Modifier
                    .padding(spacing.medium)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
@Suppress("Unused")
private fun TimelineCell(
    startEdge: Long,
    index: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val contentColor = color.takeOrElse { LocalContentColor.current }
    Canvas(modifier.fillMaxSize()) {
        val currentHeight = size.height
        val start = Instant.fromEpochMilliseconds(
            startEdge + index * HOUR_LENGTH
        )
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .toEOrSh()
        val end = Instant.fromEpochMilliseconds(
            startEdge + (index + 1) * HOUR_LENGTH
        )
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .toEOrSh()
            // cross midnight
            .let { if (it < start) it + 24 else it }

        repeat(1) { index ->
            val currentTimeline = start + (end - start) / 12 * index
            if ((currentTimeline - currentTimeline).absoluteValue < HOUR_LENGTH / 12) {
                drawLine(
                    color = contentColor,
                    start = Offset(
                        0f,
                        currentHeight * (currentTimeline - start)
                    ),
                    end = Offset(
                        size.width,
                        currentHeight * (currentTimeline - start)
                    ),
                    strokeWidth = 2f
                )
            } else {
                drawLine(
                    color = contentColor,
                    start = Offset(
                        size.width / 3f,
                        currentHeight * (currentTimeline - start)
                    ),
                    end = Offset(
                        size.width,
                        currentHeight * (currentTimeline - start)
                    )
                )
            }
        }
    }
}

@Composable
private fun ProgrammeCell(
    programme: Programme,
    inReminder: Boolean,
    modifier: Modifier = Modifier,
    onPressed: () -> Unit
) {
    val currentOnPressed by rememberUpdatedState(onPressed)
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()
    val tv = tv()
    val clockMode = preferences.twelveHourClock
    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            val start = Instant.fromEpochMilliseconds(programme.start)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .toEOrSh()
            val end = Instant.fromEpochMilliseconds(programme.end)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .toEOrSh()
            Text(
                text = "${start.formatEOrSh(clockMode)} - ${end.formatEOrSh(clockMode)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(0.65f),
                fontFamily = FontFamilies.LexendExa
            )
            Text(
                text = programme.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamilies.LexendExa
            )
            Text(
                text = programme.description,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamilies.LexendExa
            )
        }
    }
    if (!tv) {
        val hapticFeedback = LocalHapticFeedback.current
        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            label = "programme-cell-scale",
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        val currentColor by animateColorAsState(
            targetValue = if(inReminder) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.tertiaryContainer,
            label = "programme-cell-color"
        )
        val currentContentColor by animateColorAsState(
            targetValue = if(inReminder) MaterialTheme.colorScheme.onTertiary
            else MaterialTheme.colorScheme.onTertiaryContainer,
            label = "programme-cell-color"
        )
        Surface(
            color = currentColor,
            contentColor = currentContentColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = AbsoluteRoundedCornerShape(4.dp),
            modifier = Modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation()
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            down.consume()
                            currentOnPressed()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            isPressed = true
                            do {
                                val event = awaitPointerEvent()
                                event.changes.fastForEach { it.consume() }
                            } while (event.changes.fastAny { it.pressed })
                            isPressed = false
                        } finally {
                            isPressed = false
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(modifier),
            content = content
        )
    } else {
        TvSurface(
            onClick = onPressed,
            colors = TvClickableSurfaceDefaults.colors(TvMaterialTheme.colorScheme.tertiaryContainer),
            shape = TvClickableSurfaceDefaults.shape(AbsoluteRoundedCornerShape(4.dp)),
            modifier = modifier,
            content = { content() }
        )
    }
}

@Composable
private fun CurrentTimelineCell(
    milliseconds: Long,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()
    val twelveHourClock = preferences.twelveHourClock
    val color = MaterialTheme.colorScheme.error
    val contentColor = MaterialTheme.colorScheme.onError
    val currentMilliseconds by rememberUpdatedState(milliseconds)
    val time = remember(currentMilliseconds) {
        Instant
            .fromEpochMilliseconds(currentMilliseconds)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .formatEOrSh(twelveHourClock, ignoreSeconds = false)
    }
    Box(contentAlignment = Alignment.CenterEnd) {
        Canvas(
            modifier
                .requiredHeight(24.dp)
                .fillMaxWidth()
                .zIndex(2f)
        ) {
            drawLine(
                color = color,
                start = Offset(
                    x = 0f,
                    y = size.minDimension / 2
                ),
                end = Offset(
                    x = size.maxDimension,
                    y = size.minDimension / 2
                ),
                strokeWidth = Stroke.DefaultMiter
            )
        }
        Text(
            text = time,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamilies.LexendExa,
            ),
            modifier = Modifier
                .padding(horizontal = spacing.medium)
                .clip(AbsoluteRoundedCornerShape(spacing.small))
                .zIndex(4f)
                .background(color)
                .padding(horizontal = spacing.extraSmall)
        )
    }
}


@Composable
private fun Controls(
    animateToCurrentTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(
        modifier = modifier
    ) {
        val (scroll) = createRefs()
        SmallFloatingActionButton(
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            onClick = animateToCurrentTimeline,
            modifier = Modifier.constrainAs(scroll) {
                this.end.linkTo(parent.end)
                this.bottom.linkTo(parent.bottom)
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardDoubleArrowUp,
                contentDescription = "scroll to current timeline"
            )
        }
    }
}

@Composable
private fun produceCurrentMillisecondState(
    duration: Duration = 1.seconds
): State<Long> = produceState(
    initialValue = Clock.System.now().toEpochMilliseconds()
) {
    launch {
        while (true) {
            delay(duration)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
}
