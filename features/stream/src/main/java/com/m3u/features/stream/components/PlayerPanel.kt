package com.m3u.features.stream.components

//import androidx.compose.animation.ExperimentalSharedTransitionApi
//import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.Stream
import com.m3u.data.service.MediaCommand
import com.m3u.features.stream.Utils.formatEOrSh
import com.m3u.features.stream.Utils.toEOrSh
import com.m3u.material.components.Background
import com.m3u.material.components.Icon
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdges
import com.m3u.material.model.LocalSpacing
import com.m3u.material.texture.MeshContainer
import com.m3u.ui.FontFamilies
import com.m3u.ui.helper.LocalHelper
import eu.wewox.minabox.MinaBox
import eu.wewox.minabox.MinaBoxItem
import eu.wewox.minabox.rememberMinaBoxState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds


//@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun
//        SharedTransitionScope.
        PlayerPanel(
    title: String,
    playlistTitle: String,
    streamId: Int,
    isSeriesPlaylist: Boolean,
    isPanelExpanded: Boolean,
    isProgrammesRefreshing: Boolean,
    neighboring: LazyPagingItems<Stream>,
    programmes: LazyPagingItems<Programme>,
    timelineRange: ProgrammeRange,
    onRefreshProgrammesIgnoreCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Background {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = spacing.medium)
        ) {
            AnimatedVisibility(
                visible = isPanelExpanded,
                modifier = Modifier.padding(horizontal = spacing.medium)
            ) {
                Text(
                    text = title.trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .basicMarquee()
//                        .sharedElement(
//                            state = rememberSharedContentState("stream-title"),
//                            this
//                        )
                )
            }
            AnimatedVisibility(
                visible = isPanelExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.padding(horizontal = spacing.medium)
            ) {
                Text(
                    text = playlistTitle.trim().uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    color = LocalContentColor.current.copy(0.54f),
                    fontFamily = FontFamilies.LexendExa,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .basicMarquee()
//                        .sharedElement(
//                            state = rememberSharedContentState("playlist-title"),
//                            this
//                        )
                )
            }

            if (!isSeriesPlaylist) {
                PanelNeighboringSelector(
                    neighboring = neighboring,
                    streamId = streamId,
                    isPanelExpanded = isPanelExpanded
                )
            }

            PanelProgramGuide(
                isPanelExpanded = isPanelExpanded,
                isProgrammesRefreshing = isProgrammesRefreshing,
                programmes = programmes,
                onRefreshProgrammesIgnoreCache = onRefreshProgrammesIgnoreCache,
                timelineRange = timelineRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
// TODO: Support Xtream Series Episodes.
private fun PanelNeighboringSelector(
    neighboring: LazyPagingItems<Stream>,
    streamId: Int,
    isPanelExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    AnimateScrollToStreamEffect(
        neighboring = neighboring,
        streamId = streamId,
        isPanelExpanded = isPanelExpanded,
        lazyListState = lazyListState
    )

    LazyRow(
        state = lazyListState,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium),
        modifier = modifier
    ) {
        items(neighboring.itemCount) { i ->
            neighboring[i]?.let { currentStream ->
                val playing = currentStream.id == streamId
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (!playing) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.onSurface,
                        contentColor = if (!playing) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = AbsoluteRoundedCornerShape(spacing.medium),
                    elevation = CardDefaults.cardElevation(
                        if (playing) spacing.none else spacing.small
                    ),
                    onClick = {
                        coroutineScope.launch {
                            helper.play(
                                MediaCommand.Live(currentStream.id)
                            )
                        }
                    }
                ) {
                    Text(
                        text = currentStream.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold.takeIf { playing },
                        modifier = Modifier.padding(spacing.medium)
                    )
                }
            }
        }
    }
}

private enum class PanelZoom(val time: Float) {
    DEFAULT(1f), ZOOM_1_5(1.5f), ZOOM_2(2f), ZOOM_5(5f)
}

@Composable
private fun PanelProgramGuide(
    isPanelExpanded: Boolean,
    isProgrammesRefreshing: Boolean,
    programmes: LazyPagingItems<Programme>,
    timelineRange: ProgrammeRange,
    modifier: Modifier = Modifier,
    timelineWidth: Float = 128f,
    height: Float = 256f,
    padding: Float = 16f,
    eOrShSize: Float = 32f,
    scrollOffset: Int = -120,
    onRefreshProgrammesIgnoreCache: () -> Unit
) {
    val spacing = LocalSpacing.current
    val contentColor = LocalContentColor.current

    val minaBoxState = rememberMinaBoxState()
    val currentMilliseconds by produceCurrentMillisecondState()
    val coroutineScope = rememberCoroutineScope()

    var zoom: PanelZoom by remember { mutableStateOf(PanelZoom.DEFAULT) }
    val zoomModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = {
                zoom = when (zoom) {
                    PanelZoom.DEFAULT -> PanelZoom.ZOOM_1_5
                    PanelZoom.ZOOM_1_5 -> PanelZoom.ZOOM_2
                    PanelZoom.ZOOM_2 -> PanelZoom.ZOOM_5
                    PanelZoom.ZOOM_5 -> PanelZoom.DEFAULT
                }
            }
        )
    }

    val currentHeight: Float by animateFloatAsState(
        targetValue = height * zoom.time,
        label = "minabox-cell-height"
    )

    val animateToCurrentTimeline: suspend () -> Unit by rememberUpdatedState {
        minaBoxState.animateTo(
            0f,
            (currentMilliseconds - timelineRange.startEdge) / 3600000f * currentHeight + scrollOffset
        )
    }

    if (isPanelExpanded) {
        LaunchedEffect(Unit) {
            animateToCurrentTimeline()
        }
    }
    MeshContainer {
        BoxWithConstraints(zoomModifier.then(modifier), Alignment.Center) {
            MinaBox(
                state = minaBoxState,
                modifier = Modifier
                    .fillMaxSize()
                    .blurEdges(
                        MaterialTheme.colorScheme.surface,
                        listOf(Edge.Top, Edge.Bottom)
                    )
            ) {
                // timelines
                items(
                    count = timelineRange.length().floorDiv(3600000).toInt(),
                    layoutInfo = { index ->
                        MinaBoxItem(
                            x = 0f,
                            y = currentHeight * index + padding * 2.5f,
                            width = timelineWidth,
                            height = currentHeight
                        )
                    }
                ) { index ->
                    val start = remember(timelineRange.startEdge) {
                        Instant.fromEpochMilliseconds(
                            timelineRange.startEdge + index * 3600000
                        ).toEOrSh()
                    }
                    val end = remember(timelineRange.startEdge) {
                        Instant.fromEpochMilliseconds(
                            timelineRange.startEdge + (index + 1) * 3600000
                        )
                            .toEOrSh()
                            // cross midnight
                            .let { if (it < start) it + 24 else it }
                    }
                    Canvas(
                        Modifier.fillMaxSize()
                    ) {
                        var currentTimeline = start
                        while (currentTimeline <= end) {
                            if (currentTimeline.toInt().toFloat() == currentTimeline) {
                                drawLine(
                                    color = contentColor,
                                    start = Offset(
                                        size.width / 2f,
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
                                        size.width / 3f * 2,
                                        currentHeight * (currentTimeline - start)
                                    ),
                                    end = Offset(
                                        size.width,
                                        currentHeight * (currentTimeline - start)
                                    )
                                )
                            }
                            currentTimeline += 1 / 12f
                        }
                    }
                }

                // programmes
                items(
                    count = programmes.itemCount,
                    layoutInfo = { index ->
                        val programme = programmes[index]
                        if (programme != null) {
                            val start = programme.start
                            val end = programme.end
                            MinaBoxItem(
                                x = timelineWidth + padding,
                                y = currentHeight * (start - timelineRange.startEdge) / 3600000 + padding * 3, // todo
                                width = (constraints.maxWidth - timelineWidth - padding)
                                    .coerceAtLeast(0f),
                                height = (currentHeight * (end - start) / 3600000 - padding) // todo
                                    .coerceAtLeast(0f)
                            )
                        } else {
                            MinaBoxItem(0f, 0f, 0f, 0f)
                        }
                    }
                ) { index ->
                    val programme = programmes[index]
                    if (programme != null) {
                        ProgrammeCell(programme)
                    }
                }

                items(
                    count = 1,
                    layoutInfo = {
                        MinaBoxItem(
                            x = timelineWidth / 2f,
                            y = (currentMilliseconds - timelineRange.startEdge) / 3600000f * currentHeight + padding * 2,
                            width = constraints.maxWidth - timelineWidth / 2f,
                            height = eOrShSize
                        )
                    }
                ) {
                    CurrentTimeLine()
                }
            }
            ConstraintLayout(
                modifier = Modifier
                    .padding(spacing.medium)
                    .align(Alignment.BottomEnd)
            ) {
                val (refresh, scroll) = createRefs()
                SmallFloatingActionButton(
                    onClick = { coroutineScope.launch { animateToCurrentTimeline() } },
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

                AnimatedVisibility(
                    visible = !isProgrammesRefreshing,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.constrainAs(refresh) {
                        this.end.linkTo(scroll.start)
                        this.top.linkTo(scroll.top)
                        this.bottom.linkTo(scroll.bottom)
                    }
                ) {
                    SmallFloatingActionButton(onRefreshProgrammesIgnoreCache) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "refresh playlist programmes"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimateScrollToStreamEffect(
    neighboring: LazyPagingItems<Stream>,
    streamId: Int,
    isPanelExpanded: Boolean,
    lazyListState: LazyListState,
    scrollOffset: Int = -120
) {
    if (isPanelExpanded) {
        LaunchedEffect(neighboring.itemCount) {
            var index = -1
            for (i in 0 until neighboring.itemCount) {
                if (neighboring[i]?.id == streamId) {
                    index = i
                    break
                }
            }
            if (index != -1) {
                lazyListState.animateScrollToItem(index, scrollOffset)
            }
        }
    }
}

@Composable
private fun ProgrammeCell(
    programme: Programme,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, colorScheme.outline),
        shape = AbsoluteRoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            maxItemsInEachRow = 2
        ) {
            val start = Instant.fromEpochMilliseconds(programme.start).toEOrSh()
            val end = Instant.fromEpochMilliseconds(programme.end).toEOrSh()
            Text(
                text = "${start.formatEOrSh()} - ${end.formatEOrSh()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(0.65f),
                fontFamily = FontFamilies.LexendExa
            )
            ConstraintLayout {
                val (icon, title) = createRefs()
                AsyncImage(
                    model = programme.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .constrainAs(icon) {
                            this.end.linkTo(title.start, 4.dp)
                            this.top.linkTo(title.top)
                            this.bottom.linkTo(title.bottom)
                        }
                )
                Text(
                    text = programme.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamilies.LexendExa,
                    modifier = Modifier.constrainAs(title) {
                        this.end.linkTo(parent.end)
                    }
                )
            }
            Text(
                text = programme.description,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamilies.LexendExa
            )
        }
    }
}

@Composable
private fun CurrentTimeLine(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    Canvas(
        modifier
            .requiredHeight(24.dp)
            .fillMaxWidth()
            .zIndex(2f)
    ) {
        drawCircle(
            color = color,
            center = Offset(
                x = size.minDimension / 2,
                y = size.minDimension / 2
            ),
            radius = size.minDimension / 3
        )
        drawLine(
            color = color,
            start = Offset(
                x = size.minDimension / 2,
                y = size.minDimension / 2
            ),
            end = Offset(
                x = size.maxDimension,
                y = size.minDimension / 2
            ),
            strokeWidth = Stroke.DefaultMiter
        )
    }
}

@Composable
private fun produceCurrentMillisecondState(): State<Long> = produceState(
    initialValue = Clock.System.now().toEpochMilliseconds()
) {
    launch {
        while (true) {
            delay(1.seconds)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
}
