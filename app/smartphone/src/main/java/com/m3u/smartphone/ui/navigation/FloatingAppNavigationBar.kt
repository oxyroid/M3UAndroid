package com.m3u.smartphone.ui.navigation

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.m3u.smartphone.ui.material.components.Destination
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal const val FLOATING_NAVIGATION_TEST_TAG = "floating-app-navigation"

internal class FloatingNavigationSettleController {
    var job: Job? = null
    var generation: Long = 0L
        private set

    fun invalidate(): Long {
        generation += 1L
        job?.cancel()
        job = null
        return generation
    }

    fun isCurrent(candidate: Long): Boolean = candidate == generation
}

@Composable
internal fun FloatingAppNavigationBar(
    selectedDestination: Destination?,
    backdrop: Backdrop,
    onDestinationSelected: (Destination) -> Unit,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = Destination.entries
    val selectedIndex = destinations.indexOf(selectedDestination).coerceAtLeast(0)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val animationScope = rememberCoroutineScope()
    val currentOnDestinationSelected by rememberUpdatedState(onDestinationSelected)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val settleController = remember { FloatingNavigationSettleController() }
    val navigationContentBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = rememberCombinedBackdrop(
        backdrop,
        navigationContentBackdrop,
    )
    val indicatorPosition = remember(destinations.size) {
        Animatable(selectedIndex.toFloat(), visibilityThreshold = 0.001f)
    }
    val deformationVelocity = remember {
        Animatable(0f, visibilityThreshold = 0.01f)
    }
    val panelDragDistance = remember { Animatable(0f) }
    val interactionProgress = remember {
        Animatable(0f, visibilityThreshold = 0.001f)
    }
    val interactionSources = remember(destinations.size) {
        List(destinations.size) { MutableInteractionSource() }
    }
    val pressedStates = interactionSources.map { source ->
        source.collectIsPressedAsState()
    }
    val isAnyItemPressed = pressedStates.any { it.value }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }

    fun startVisualSettle(
        targetIndex: Int,
        startPosition: Float? = null,
        startVelocityItemsPerSecond: Float? = null,
        startPanelDistancePx: Float? = null,
        onPositionSettled: (() -> Unit)? = null,
    ) {
        val generation = settleController.invalidate()
        isSettling = true
        val job = animationScope.launch {
            try {
                startPosition?.let { indicatorPosition.snapTo(it) }
                startVelocityItemsPerSecond?.let { deformationVelocity.snapTo(it) }
                startPanelDistancePx?.let { panelDragDistance.snapTo(it) }
                coroutineScope {
                    val positionJob = launch {
                        indicatorPosition.animateTo(
                            targetValue = targetIndex.toFloat(),
                            animationSpec = spring(
                                dampingRatio = 1f,
                                stiffness = 1000f,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    }
                    launch {
                        deformationVelocity.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.5f,
                                stiffness = 300f,
                                visibilityThreshold = 0.01f,
                            ),
                        )
                    }
                    launch {
                        panelDragDistance.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 1f,
                                stiffness = 300f,
                                visibilityThreshold = 0.5f,
                            ),
                        )
                    }
                    positionJob.join()
                    if (settleController.isCurrent(generation)) {
                        onPositionSettled?.invoke()
                    }
                }
            } finally {
                if (settleController.isCurrent(generation)) {
                    settleController.job = null
                    isSettling = false
                }
            }
        }
        if (settleController.isCurrent(generation) && job.isActive) {
            settleController.job = job
        }
    }

    LaunchedEffect(isAnyItemPressed, isDragging, isSettling) {
        val interacting = isAnyItemPressed || isDragging || isSettling
        interactionProgress.animateTo(
            targetValue = if (interacting) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (interacting) {
                    INDICATOR_EXPAND_DURATION_MILLIS
                } else {
                    INDICATOR_COLLAPSE_DURATION_MILLIS
                },
                easing = if (interacting) EaseOut else FastOutSlowInEasing,
            ),
        )
    }

    LaunchedEffect(selectedIndex) {
        isDragging = false
        val needsVisualSettle =
            abs(indicatorPosition.value - selectedIndex.toFloat()) > 0.001f ||
                abs(deformationVelocity.value) > 0.01f ||
                abs(panelDragDistance.value) > 0.5f
        if (needsVisualSettle) {
            startVisualSettle(targetIndex = selectedIndex)
        } else {
            settleController.invalidate()
            isSettling = false
        }
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shellSurfaceColor = MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.40f else 0.92f,
    )
    val idleIndicatorColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val navigationWidth = calculateFloatingNavigationWidth(
            containerWidth = maxWidth,
            itemCount = destinations.size,
        )
        val itemWidth = (navigationWidth - FLOATING_NAVIGATION_INNER_PADDING * 2) /
            destinations.size
        val itemWidthPx = with(density) { itemWidth.toPx() }
        val navigationWidthPx = with(density) { navigationWidth.toPx() }
        val innerPaddingPx = with(density) { FLOATING_NAVIGATION_INNER_PADDING.toPx() }
        LaunchedEffect(itemWidthPx, layoutDirection) {
            settleController.invalidate()
            isDragging = false
            isSettling = false
            indicatorPosition.snapTo(currentSelectedIndex.toFloat())
            deformationVelocity.snapTo(0f)
            panelDragDistance.snapTo(0f)
        }
        val indicatorTopPx = with(density) {
            ((FLOATING_NAVIGATION_HEIGHT - FLOATING_NAVIGATION_INDICATOR_HEIGHT) / 2).toPx()
        }
        val panelOffsetPx = with(density) {
            val fraction = (panelDragDistance.value / navigationWidthPx)
                .coerceIn(-1f, 1f)
            PANEL_TRANSLATION_LIMIT.toPx() *
                fraction.sign *
                EaseOut.transform(abs(fraction))
        }
        val indicatorTransform = resolveFloatingNavigationIndicatorTransform(
            interactionProgress = interactionProgress.value,
            velocityItemsPerSecond = deformationVelocity.value,
        )
        val shellScale = 1f +
            with(density) { SHELL_INTERACTION_EXPANSION.toPx() } /
            navigationWidthPx *
            interactionProgress.value
        val indicatorX = resolveFloatingNavigationIndicatorPhysicalX(
            position = indicatorPosition.value,
            itemWidthPx = itemWidthPx,
            navigationWidthPx = navigationWidthPx,
            innerPaddingPx = innerPaddingPx,
            isRtl = layoutDirection == LayoutDirection.Rtl,
        )

        Box(
            modifier = Modifier
                .width(navigationWidth)
                .height(FLOATING_NAVIGATION_HEIGHT)
                .onSizeChanged { size ->
                    onHeightChanged(with(density) { size.height.toDp() })
                }
                .testTag(FLOATING_NAVIGATION_TEST_TAG),
        ) {
            NavigationGlassContent(
                destinations = destinations,
                itemWidth = itemWidth,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                useSelectedIcons = false,
                iconScale = 1f + 0.20f * interactionProgress.value,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = panelOffsetPx
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                refractionHeight = 24.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                                depthEffect = true,
                            )
                        },
                        layerBlock = {
                            scaleX = shellScale
                            scaleY = shellScale
                        },
                        onDrawSurface = {
                            drawRect(shellSurfaceColor)
                        },
                    )
                    .padding(FLOATING_NAVIGATION_INNER_PADDING),
            )

            NavigationGlassContent(
                destinations = destinations,
                itemWidth = itemWidth,
                iconColor = MaterialTheme.colorScheme.primary,
                useSelectedIcons = true,
                iconScale = 1f + 0.20f * interactionProgress.value,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(navigationContentBackdrop)
                    .graphicsLayer {
                        translationX = panelOffsetPx
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                refractionHeight = 24.dp.toPx() * interactionProgress.value,
                                refractionAmount = 24.dp.toPx() * interactionProgress.value,
                                depthEffect = true,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = interactionProgress.value)
                        },
                        onDrawSurface = {
                            drawRect(shellSurfaceColor)
                        },
                    )
                    .height(FLOATING_NAVIGATION_INDICATOR_HEIGHT)
                    .fillMaxWidth()
                    .padding(horizontal = FLOATING_NAVIGATION_INNER_PADDING),
            )

            Box(
                modifier = Modifier
                    .absoluteOffset {
                        IntOffset(
                            x = (indicatorX + panelOffsetPx).roundToInt(),
                            y = indicatorTopPx.roundToInt(),
                        )
                    }
                    .width(itemWidth)
                    .height(FLOATING_NAVIGATION_INDICATOR_HEIGHT)
                    .drawBackdrop(
                        backdrop = indicatorBackdrop,
                        shape = { CircleShape },
                        effects = {
                            lens(
                                refractionHeight = 10.dp.toPx() *
                                    interactionProgress.value,
                                refractionAmount = 14.dp.toPx() *
                                    interactionProgress.value,
                                depthEffect = true,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = interactionProgress.value)
                        },
                        shadow = {
                            Shadow(alpha = interactionProgress.value)
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * interactionProgress.value,
                                alpha = interactionProgress.value,
                            )
                        },
                        layerBlock = {
                            scaleX = indicatorTransform.scaleX
                            scaleY = indicatorTransform.scaleY
                        },
                        onDrawSurface = {
                            drawRect(
                                color = idleIndicatorColor,
                                alpha = 1f - interactionProgress.value,
                            )
                            drawRect(
                                color = Color.Black.copy(
                                    alpha = 0.03f * interactionProgress.value,
                                ),
                            )
                        },
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(FLOATING_NAVIGATION_INNER_PADDING)
                    .pointerInput(
                        destinations.size,
                        itemWidthPx,
                        layoutDirection,
                    ) {
                        var dragUpdateJob: Job? = null
                        try {
                            coroutineScope {
                                awaitEachGesture {
                                    val velocityTracker = VelocityTracker()
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                                    val gestureGeneration = settleController.invalidate()
                                    isSettling = false

                                    var overSlop = 0f
                                    val dragStart = awaitHorizontalTouchSlopOrCancellation(
                                        pointerId = down.id,
                                    ) { change, over ->
                                        change.consume()
                                        overSlop = over
                                    }
                                    if (dragStart == null) {
                                        if (settleController.isCurrent(gestureGeneration)) {
                                            startVisualSettle(
                                                targetIndex = currentSelectedIndex,
                                            )
                                        }
                                        return@awaitEachGesture
                                    }
                                    if (!settleController.isCurrent(gestureGeneration)) {
                                        return@awaitEachGesture
                                    }

                                    isDragging = true

                                    val logicalDirection = if (
                                        layoutDirection == LayoutDirection.Ltr
                                    ) {
                                        1f
                                    } else {
                                        -1f
                                    }
                                    val lastItemPosition = (destinations.size - 1).toFloat()
                                    var desiredPosition = indicatorPosition.value
                                    var visualPosition = indicatorPosition.value
                                    var previousVisualPosition = visualPosition
                                    var previousUptimeMillis = dragStart.uptimeMillis
                                    var accumulatedPanelDistance = panelDragDistance.value

                                    fun scheduleDragUpdate(
                                        dragAmountPx: Float,
                                        uptimeMillis: Long,
                                    ) {
                                        if (!settleController.isCurrent(gestureGeneration)) {
                                            return
                                        }
                                        desiredPosition = applyFloatingNavigationDrag(
                                            desiredPosition = desiredPosition,
                                            deltaItems = dragAmountPx /
                                                itemWidthPx *
                                                logicalDirection,
                                            itemCount = destinations.size,
                                        )
                                        visualPosition = desiredPosition.coerceIn(
                                            minimumValue = 0f,
                                            maximumValue = lastItemPosition,
                                        )
                                        val elapsedSeconds = (
                                            uptimeMillis - previousUptimeMillis
                                            ).coerceAtLeast(1L) / 1000f
                                        val visualVelocity = (
                                            visualPosition - previousVisualPosition
                                            ) / elapsedSeconds
                                        previousVisualPosition = visualPosition
                                        previousUptimeMillis = uptimeMillis
                                        accumulatedPanelDistance += dragAmountPx

                                        dragUpdateJob?.cancel()
                                        dragUpdateJob = launch {
                                            if (
                                                !settleController.isCurrent(
                                                    gestureGeneration,
                                                )
                                            ) {
                                                return@launch
                                            }
                                            indicatorPosition.snapTo(visualPosition)
                                            if (
                                                !settleController.isCurrent(
                                                    gestureGeneration,
                                                )
                                            ) {
                                                return@launch
                                            }
                                            deformationVelocity.snapTo(visualVelocity)
                                            if (
                                                !settleController.isCurrent(
                                                    gestureGeneration,
                                                )
                                            ) {
                                                return@launch
                                            }
                                            panelDragDistance.snapTo(accumulatedPanelDistance)
                                        }
                                    }

                                    scheduleDragUpdate(
                                        dragAmountPx = overSlop,
                                        uptimeMillis = dragStart.uptimeMillis,
                                    )
                                    velocityTracker.addPosition(
                                        dragStart.uptimeMillis,
                                        dragStart.position,
                                    )
                                    val completed = horizontalDrag(dragStart.id) { change ->
                                        change.consume()
                                        velocityTracker.addPosition(
                                            change.uptimeMillis,
                                            change.position,
                                        )
                                        scheduleDragUpdate(
                                            dragAmountPx = change.position.x -
                                                change.previousPosition.x,
                                            uptimeMillis = change.uptimeMillis,
                                        )
                                    }
                                    dragUpdateJob?.cancel()
                                    if (!settleController.isCurrent(gestureGeneration)) {
                                        isDragging = false
                                        return@awaitEachGesture
                                    }

                                    val physicalVelocity = if (completed) {
                                        velocityTracker.calculateVelocity().x
                                    } else {
                                        0f
                                    }
                                    val logicalVelocity =
                                        physicalVelocity * logicalDirection
                                    val targetIndex = resolveFloatingNavigationReleaseTarget(
                                        desiredPosition = desiredPosition,
                                        velocityPxPerSecond = logicalVelocity,
                                        itemWidthPx = itemWidthPx,
                                        itemCount = destinations.size,
                                    )

                                    isDragging = false
                                    startVisualSettle(
                                        targetIndex = targetIndex,
                                        startPosition = visualPosition,
                                        startVelocityItemsPerSecond =
                                            logicalVelocity / itemWidthPx,
                                        startPanelDistancePx = accumulatedPanelDistance,
                                        onPositionSettled = {
                                            if (targetIndex != currentSelectedIndex) {
                                                currentOnDestinationSelected(
                                                    destinations[targetIndex],
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        } finally {
                            dragUpdateJob?.cancel()
                            settleController.invalidate()
                            isDragging = false
                            isSettling = false
                        }
                    },
            ) {
                destinations.forEachIndexed { index, destination ->
                    val label = stringResource(destination.iconTextId)
                    val selectDestination = {
                        isDragging = false
                        startVisualSettle(targetIndex = index)
                        currentOnDestinationSelected(destination)
                    }
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clearAndSetSemantics {
                                contentDescription = label
                                role = Role.Tab
                                selected = index == selectedIndex
                                onClick(label = label) {
                                    selectDestination()
                                    true
                                }
                            }
                            .selectable(
                                selected = index == selectedIndex,
                                interactionSource = interactionSources[index],
                                indication = null,
                                role = Role.Tab,
                                onClick = selectDestination,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationGlassContent(
    destinations: List<Destination>,
    itemWidth: Dp,
    iconColor: Color,
    useSelectedIcons: Boolean,
    iconScale: Float,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        destinations.forEach { destination ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(itemWidth)
                    .fillMaxHeight(),
            ) {
                Icon(
                    imageVector = if (useSelectedIcons) {
                        destination.selectedIcon
                    } else {
                        destination.unselectedIcon
                    },
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .size(NAVIGATION_ICON_SIZE)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }
    }
}

private val FLOATING_NAVIGATION_HEIGHT = 64.dp
private val FLOATING_NAVIGATION_INDICATOR_HEIGHT = 56.dp
private val FLOATING_NAVIGATION_INNER_PADDING = 4.dp
private val NAVIGATION_ICON_SIZE = 24.dp
private val PANEL_TRANSLATION_LIMIT = 4.dp
private val SHELL_INTERACTION_EXPANSION = 16.dp
private const val INDICATOR_EXPAND_DURATION_MILLIS = 90
private const val INDICATOR_COLLAPSE_DURATION_MILLIS = 220
