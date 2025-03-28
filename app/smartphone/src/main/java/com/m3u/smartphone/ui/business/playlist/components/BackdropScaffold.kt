package com.m3u.smartphone.ui.business.playlist.components

import androidx.annotation.FloatRange
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import kotlin.jvm.JvmName
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Possible values of [BackdropScaffoldState].
 */
enum class BackdropValue {
    /**
     * Indicates the back layer is concealed and the front layer is active.
     */
    Concealed,

    /**
     * Indicates the back layer is revealed and the front layer is inactive.
     */
    Revealed
}

/**
 * State of the persistent bottom sheet in [BackdropScaffold].
 *
 * @param initialValue The initial value of the state.
 * @param density The density that this state can use to convert values to and from dp.
 * @param animationSpec The default animation that will be used to animate to a new state.
 * @param confirmValueChange Optional callback invoked to confirm or veto a pending state change.
 * @param snackbarHostState The [SnackbarHostState] used to show snackbars inside the scaffold.
 */
@Suppress("Deprecation")
@Stable
fun BackdropScaffoldState(
    initialValue: BackdropValue,
    density: Density,
    animationSpec: AnimationSpec<Float> = BackdropScaffoldDefaults.AnimationSpec,
    decayAnimationSpec: DecayAnimationSpec<Float> = BackdropScaffoldDefaults.DecayAnimationSpec,
    confirmValueChange: (BackdropValue) -> Boolean = { true },
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
) = BackdropScaffoldState(
    initialValue,
    animationSpec,
    decayAnimationSpec,
    confirmValueChange,
    snackbarHostState
).also {
    it.density = density
}

/**
 * State of the [BackdropScaffold] composable.
 *
 * @param initialValue The initial value of the state.
 * @param animationSpec The default animation that will be used to animate to a new state.
 * @param confirmValueChange Optional callback invoked to confirm or veto a pending state change.
 * @param snackbarHostState The [SnackbarHostState] used to show snackbars inside the scaffold.
 */
@Stable
class BackdropScaffoldState @Deprecated(
    "This constructor is deprecated. Density must be provided by the component. " +
            "Please use the constructor that provides a [Density].",
    ReplaceWith(
        """
            BackdropScaffoldState(
                initialValue = initialValue,
                density = LocalDensity.current,
                animationSpec = animationSpec,
                confirmValueChange = confirmValueChange
            )
            """
    )
) constructor(
    initialValue: BackdropValue,
    animationSpec: AnimationSpec<Float> = BackdropScaffoldDefaults.AnimationSpec,
    decayAnimationSpec: DecayAnimationSpec<Float> = BackdropScaffoldDefaults.DecayAnimationSpec,
    val confirmValueChange: (BackdropValue) -> Boolean = { true },
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) {
    /**
     * The current value of the [BottomSheetState].
     */
    val currentValue: BackdropValue
        get() = anchoredDraggableState.currentValue

    /**
     * The target value the state will settle at once the current interaction ends, or the
     * [currentValue] if there is no interaction in progress.
     */
    val targetValue: BackdropValue
        get() = anchoredDraggableState.targetValue

    /**
     * Require the current offset.
     *
     * @throws IllegalStateException If the offset has not been initialized yet
     */
    fun requireOffset() = anchoredDraggableState.requireOffset()

    /**
     * Whether the back layer is revealed.
     */
    val isRevealed: Boolean
        get() = anchoredDraggableState.currentValue == BackdropValue.Revealed

    /**
     * Whether the back layer is concealed.
     */
    val isConcealed: Boolean
        get() = anchoredDraggableState.currentValue == BackdropValue.Concealed

    /**
     * Reveal the back layer with animation and suspend until it if fully revealed or animation
     * has been cancelled.  This method will throw [CancellationException] if the animation is
     * interrupted
     */
    suspend fun reveal() = anchoredDraggableState.animateTo(targetValue = BackdropValue.Revealed)

    /**
     * Conceal the back layer with animation and suspend until it if fully concealed or animation
     * has been cancelled. This method will throw [CancellationException] if the animation is
     * interrupted
     */
    suspend fun conceal() = anchoredDraggableState.animateTo(targetValue = BackdropValue.Concealed)

    /**
     * The fraction of the offset between [from] and [to], as a fraction between [0f..1f], or 1f if
     * [from] is equal to [to].
     *
     * @param from The starting value used to calculate the distance
     * @param to The end value used to calculate the distance
     */
    @FloatRange(from = 0.0, to = 1.0)
    fun progress(
        from: BackdropValue,
        to: BackdropValue
    ): Float {
        val fromOffset = anchoredDraggableState.anchors.positionOf(from)
        val toOffset = anchoredDraggableState.anchors.positionOf(to)
        val currentOffset = anchoredDraggableState.offset.coerceIn(
            min(fromOffset, toOffset), // fromOffset might be > toOffset
            max(fromOffset, toOffset)
        )
        val fraction = (currentOffset - fromOffset) / (toOffset - fromOffset)
        return if (fraction.isNaN()) 1f else abs(fraction)
    }

    internal val anchoredDraggableState = AnchoredDraggableState(
        initialValue = initialValue,
        positionalThreshold = {
            with(requireDensity()) {
                PositionalThreshold.toPx()
            }
        },
        velocityThreshold = {
            with(requireDensity()) {
                VelocityThreshold.toPx()
            }
        },
        snapAnimationSpec = animationSpec,
        decayAnimationSpec = decayAnimationSpec,
        confirmValueChange = confirmValueChange,
    )

    internal var density: Density? = null
    private fun requireDensity() = requireNotNull(density) {
        "The density on BackdropScaffoldState ($this) was not set." +
                " Did you use BackdropScaffoldState with " +
                "the BackdropScaffold composable?"
    }

    internal val nestedScrollConnection = ConsumeSwipeNestedScrollConnection(
        anchoredDraggableState,
        Orientation.Vertical
    )

    companion object {

        /**
         * The default [Saver] implementation for [BackdropScaffoldState].
         */
        fun Saver(
            animationSpec: AnimationSpec<Float>,
            confirmStateChange: (BackdropValue) -> Boolean,
            snackbarHostState: SnackbarHostState,
            density: Density
        ): Saver<BackdropScaffoldState, *> = Saver(
            save = { it.anchoredDraggableState.currentValue },
            restore = {
                BackdropScaffoldState(
                    initialValue = it,
                    animationSpec = animationSpec,
                    confirmValueChange = confirmStateChange,
                    snackbarHostState = snackbarHostState,
                    density = density
                )
            }
        )
    }
}

/**
 * Create and [remember] a [BackdropScaffoldState].
 *
 * @param initialValue The initial value of the state.
 * @param animationSpec The default animation that will be used to animate to a new state.
 * @param confirmStateChange Optional callback invoked to confirm or veto a pending state change.
 * @param snackbarHostState The [SnackbarHostState] used to show snackbars inside the scaffold.
 */
@Composable
fun rememberBackdropScaffoldState(
    initialValue: BackdropValue,
    animationSpec: AnimationSpec<Float> = BackdropScaffoldDefaults.AnimationSpec,
    confirmStateChange: (BackdropValue) -> Boolean = { true },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
): BackdropScaffoldState {
    val density = LocalDensity.current
    return rememberSaveable(
        animationSpec,
        confirmStateChange,
        snackbarHostState,
        saver = BackdropScaffoldState.Saver(
            animationSpec = animationSpec,
            confirmStateChange = confirmStateChange,
            snackbarHostState = snackbarHostState,
            density = density
        )
    ) {
        BackdropScaffoldState(
            initialValue = initialValue,
            animationSpec = animationSpec,
            confirmValueChange = confirmStateChange,
            snackbarHostState = snackbarHostState,
            density = density
        )
    }
}

/**
 * <a href="https://material.io/components/backdrop" class="external" target="_blank">Material Design backdrop</a>.
 *
 * A backdrop appears behind all other surfaces in an app, displaying contextual and actionable
 * content.
 *
 * ![Backdrop image](https://developer.android.com/images/reference/androidx/compose/material/backdrop.png)
 *
 * This component provides an API to put together several material components to construct your
 * screen. For a similar component which implements the basic material design layout strategy
 * with app bars, floating action buttons and navigation drawers, use the standard [Scaffold].
 * For similar component that uses a bottom sheet as the centerpiece of the screen, use
 * [BottomSheetScaffold].
 *
 * Either the back layer or front layer can be active at a time. When the front layer is active,
 * it sits at an offset below the top of the screen. This is the [peekHeight] and defaults to
 * 56dp which is the default app bar height. When the front layer is inactive, it sticks to the
 * height of the back layer's content if [stickyFrontLayer] is set to `true` and the height of
 * the front layer exceeds the [headerHeight], and otherwise it minimizes to the [headerHeight].
 * To switch between the back layer and front layer, you can either swipe on the front layer if
 * [gesturesEnabled] is set to `true` or use any of the methods in [BackdropScaffoldState].
 *
 * The scaffold also contains an app bar, which by default is placed above the back layer's
 * content. If [persistentAppBar] is set to `false`, then the backdrop will not show the app bar
 * when the back layer is revealed; instead it will switch between the app bar and the provided
 * content with an animation. For best results, the [peekHeight] should match the app bar height.
 * To show a snackbar, use the method `showSnackbar` of [BackdropScaffoldState.snackbarHostState].
 *
 * A simple example of a backdrop scaffold looks like this:
 *
 *
 * @param appBar App bar for the back layer. Make sure that the [peekHeight] is equal to the
 * height of the app bar, so that the app bar is fully visible. Consider using [TopAppBar] but
 * set the elevation to 0dp and background color to transparent as a surface is already provided.
 * @param backLayerContent The content of the back layer.
 * @param frontLayerContent The content of the front layer.
 * @param modifier Optional [Modifier] for the root of the scaffold.
 * @param scaffoldState The state of the scaffold.
 * @param snackbarHost The component hosting the snackbars shown inside the scaffold.
 * @param gesturesEnabled Whether or not the backdrop can be interacted with by gestures.
 * @param peekHeight The height of the visible part of the back layer when it is concealed.
 * @param headerHeight The minimum height of the front layer when it is inactive.
 * @param persistentAppBar Whether the app bar should be shown when the back layer is revealed.
 * By default, it will always be shown above the back layer's content. If this is set to `false`,
 * the back layer will automatically switch between the app bar and its content with an animation.
 * @param stickyFrontLayer Whether the front layer should stick to the height of the back layer.
 * @param backLayerBackgroundColor The background color of the back layer.
 * @param backLayerContentColor The preferred content color provided by the back layer to its
 * children. Defaults to the matching content color for [backLayerBackgroundColor], or if that
 * is not a color from the theme, this will keep the same content color set above the back layer.
 * @param frontLayerShape The shape of the front layer.
 * @param frontLayerElevation The elevation of the front layer.
 * @param frontLayerBackgroundColor The background color of the front layer.
 * @param frontLayerContentColor The preferred content color provided by the back front to its
 * children. Defaults to the matching content color for [frontLayerBackgroundColor], or if that
 * is not a color from the theme, this will keep the same content color set above the front layer.
 * @param frontLayerScrimColor The color of the scrim applied to the front layer when the back
 * layer is revealed. If the color passed is [Color.Unspecified], then a scrim will not be
 * applied and interaction with the front layer will not be blocked when the back layer is revealed.
 */
@Composable
fun BackdropScaffold(
    appBar: @Composable () -> Unit,
    backLayerContent: @Composable () -> Unit,
    frontLayerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: BackdropScaffoldState = rememberBackdropScaffoldState(BackdropValue.Concealed),
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
    gesturesEnabled: Boolean = true,
    peekHeight: Dp = BackdropScaffoldDefaults.PeekHeight,
    headerHeight: Dp = BackdropScaffoldDefaults.HeaderHeight,
    persistentAppBar: Boolean = true,
    stickyFrontLayer: Boolean = true,
    backLayerBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    backLayerContentColor: Color = contentColorFor(backLayerBackgroundColor),
    frontLayerShape: Shape = BackdropScaffoldDefaults.frontLayerShape,
    frontLayerElevation: Dp = BackdropScaffoldDefaults.FrontLayerElevation,
    frontLayerBackgroundColor: Color = MaterialTheme.colorScheme.surface,
    frontLayerContentColor: Color = contentColorFor(frontLayerBackgroundColor),
    frontLayerScrimColor: Color = BackdropScaffoldDefaults.frontLayerScrimColor,
) {
    // b/278692145 Remove this once deprecated methods without density are removed
    val density = LocalDensity.current
    SideEffect {
        scaffoldState.density = density
    }

    val peekHeightPx = with(LocalDensity.current) { peekHeight.toPx() }
    val headerHeightPx = with(LocalDensity.current) { headerHeight.toPx() }

    val backLayer = @Composable {
        if (persistentAppBar) {
            Column {
                appBar()
                backLayerContent()
            }
        } else {
            BackLayerTransition(
                scaffoldState.anchoredDraggableState.targetValue,
                appBar, backLayerContent
            )
        }
    }
    val calculateBackLayerConstraints: (Constraints) -> Constraints = {
        it.copy(minWidth = 0, minHeight = 0).offset(vertical = -headerHeightPx.roundToInt())
    }

    val state = scaffoldState.anchoredDraggableState

    // Back layer
    Surface(
        color = backLayerBackgroundColor,
        contentColor = backLayerContentColor
    ) {
        val scope = rememberCoroutineScope()
        BackdropStack(
            modifier.fillMaxSize(),
            backLayer,
            calculateBackLayerConstraints
        ) { constraints, backLayerHeight ->
            var revealedHeight = constraints.maxHeight - headerHeightPx
            if (stickyFrontLayer) {
                revealedHeight = min(revealedHeight, backLayerHeight)
            }

            val nestedScroll = if (gesturesEnabled) {
                Modifier.nestedScroll(scaffoldState.nestedScrollConnection)
            } else {
                Modifier
            }

            // Front layer
            Surface(
                modifier = nestedScroll
                    .draggableAnchors(state, Orientation.Vertical) { layoutSize, _ ->
                        val sheetHeight = layoutSize.height.toFloat()
                        val collapsedHeight = layoutSize.height - peekHeightPx
                        val newAnchors = DraggableAnchors {
                            if (sheetHeight == 0f || sheetHeight == peekHeightPx) {
                                BackdropValue.Concealed at collapsedHeight
                            } else {
                                BackdropValue.Concealed at peekHeightPx
                                BackdropValue.Revealed at revealedHeight
                            }
                        }
                        val newTarget = when (scaffoldState.targetValue) {
                            BackdropValue.Concealed -> BackdropValue.Concealed
                            BackdropValue.Revealed -> if (newAnchors.hasAnchorFor(BackdropValue.Revealed)) BackdropValue.Revealed
                            else BackdropValue.Concealed
                        }
                        return@draggableAnchors newAnchors to newTarget
                    }
                    .anchoredDraggable(
                        state = state,
                        orientation = Orientation.Vertical,
                        enabled = gesturesEnabled,
                    )
                    .semantics {
                        if (scaffoldState.isConcealed) {
                            collapse {
                                if (scaffoldState.confirmValueChange(BackdropValue.Revealed)) {
                                    scope.launch { scaffoldState.reveal() }
                                }; true
                            }
                        } else {
                            expand {
                                if (scaffoldState.confirmValueChange(BackdropValue.Concealed)) {
                                    scope.launch { scaffoldState.conceal() }
                                }; true
                            }
                        }
                    },
                shape = frontLayerShape,
                // TODO
                tonalElevation = frontLayerElevation,
                color = frontLayerBackgroundColor,
                contentColor = frontLayerContentColor
            ) {
                Box(Modifier.padding(bottom = peekHeight)) {
                    frontLayerContent()
                    Scrim(
                        color = frontLayerScrimColor,
                        onDismiss = {
                            if (gesturesEnabled && scaffoldState.confirmValueChange(BackdropValue.Concealed)) {
                                scope.launch { scaffoldState.conceal() }
                            }
                        },
                        visible = scaffoldState.targetValue == BackdropValue.Revealed
                    )
                }
            }

            // Snackbar host
            Box(
                Modifier
                    .padding(
                        bottom = if (scaffoldState.isRevealed &&
                            revealedHeight == constraints.maxHeight - headerHeightPx
                        ) headerHeight else 0.dp
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                snackbarHost(scaffoldState.snackbarHostState)
            }
        }
    }
}

@Composable
private fun Scrim(
    color: Color,
    onDismiss: () -> Unit,
    visible: Boolean
) {
    if (color.isSpecified) {
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = TweenSpec()
        )
        val dismissModifier = if (visible) {
            Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } }
        } else {
            Modifier
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .then(dismissModifier)
        ) {
            drawRect(color = color, alpha = alpha)
        }
    }
}

/**
 * A shared axis transition, used in the back layer. Both the [appBar] and the [content] shift
 * vertically, while they crossfade. It is very important that both are composed and measured,
 * even if invisible, and that this component is as large as both of them.
 */
@Composable
private fun BackLayerTransition(
    target: BackdropValue,
    appBar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    // The progress of the animation between Revealed (0) and Concealed (2).
    // The midpoint (1) is the point where the appBar and backContent are switched.
    val animationProgress by animateFloatAsState(
        targetValue = if (target == BackdropValue.Revealed) 0f else 2f, animationSpec = TweenSpec()
    )
    val animationSlideOffset = with(LocalDensity.current) { AnimationSlideOffset.toPx() }

    Box {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val appBarFloat = (animationProgress - 1).fastCoerceIn(0f, 1f)
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0, zIndex = appBarFloat)
                    }
                }
                .graphicsLayer {
                    val appBarFloat = (animationProgress - 1).fastCoerceIn(0f, 1f)
                    alpha = appBarFloat
                    translationY = (1 - appBarFloat) * animationSlideOffset
                }
        ) {
            appBar()
        }
        Box(
            @Suppress("SuspiciousIndentation") // b/320904953
            Modifier
                .layout { measurable, constraints ->
                    val contentFloat = (1 - animationProgress).fastCoerceIn(0f, 1f)
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0, zIndex = contentFloat)
                    }
                }
                .graphicsLayer {
                    val contentFloat = (1 - animationProgress).fastCoerceIn(0f, 1f)
                    alpha = contentFloat
                    translationY = (1 - contentFloat) * animationSlideOffset
                }
        ) {
            content()
        }
    }
}

@Composable
private fun BackdropStack(
    modifier: Modifier,
    backLayer: @Composable () -> Unit,
    calculateBackLayerConstraints: (Constraints) -> Constraints,
    frontLayer: @Composable (Constraints, Float) -> Unit
) {
    SubcomposeLayout(modifier) { constraints ->
        val backLayerPlaceable =
            subcompose(BackdropLayers.Back, backLayer).first()
                .measure(calculateBackLayerConstraints(constraints))

        val backLayerHeight = backLayerPlaceable.height.toFloat()

        val placeables =
            subcompose(BackdropLayers.Front) {
                frontLayer(constraints, backLayerHeight)
            }.fastMap { it.measure(constraints) }

        var maxWidth = max(constraints.minWidth, backLayerPlaceable.width)
        var maxHeight = max(constraints.minHeight, backLayerPlaceable.height)
        placeables.fastForEach {
            maxWidth = max(maxWidth, it.width)
            maxHeight = max(maxHeight, it.height)
        }

        layout(maxWidth, maxHeight) {
            backLayerPlaceable.placeRelative(0, 0)
            placeables.fastForEach { it.placeRelative(0, 0) }
        }
    }
}

private enum class BackdropLayers { Back, Front }

/**
 * Contains useful defaults for [BackdropScaffold].
 */
object BackdropScaffoldDefaults {

    /**
     * The default peek height of the back layer.
     */
    val PeekHeight = 56.dp

    /**
     * The default header height of the front layer.
     */
    val HeaderHeight = 48.dp

    /**
     * The default shape of the front layer.
     */
    val frontLayerShape: Shape
        @Composable
        get() = MaterialTheme.shapes.large
            .copy(topStart = CornerSize(16.dp), topEnd = CornerSize(16.dp))

    /**
     * The default elevation of the front layer.
     */
    val FrontLayerElevation = 1.dp

    /**
     * The default color of the scrim applied to the front layer.
     */
    val frontLayerScrimColor: Color
        @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)

    /**
     * The default animation spec used by [BottomSheetScaffoldState].
     */
    val AnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    val DecayAnimationSpec: DecayAnimationSpec<Float> = exponentialDecay()
}

private val AnimationSlideOffset = 20.dp
private val VelocityThreshold = 125.dp
private val PositionalThreshold = 56.dp

internal fun ConsumeSwipeNestedScrollConnection(
    state: AnchoredDraggableState<*>,
    orientation: Orientation
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.toFloat()
        return if (delta < 0 && source == NestedScrollSource.UserInput) {
            state.dispatchRawDelta(delta).toOffset()
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        return if (source == NestedScrollSource.UserInput) {
            state.dispatchRawDelta(available.toFloat()).toOffset()
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val toFling = available.toFloat()
        val currentOffset = state.requireOffset()
        return if (toFling < 0 && currentOffset > state.anchors.minAnchor()) {
            state.settle(velocity = toFling)
            // since we go to the anchor with tween settling, consume all for the best UX
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        state.settle(velocity = available.toFloat())
        return available
    }

    private fun Float.toOffset(): Offset = Offset(
        x = if (orientation == Orientation.Horizontal) this else 0f,
        y = if (orientation == Orientation.Vertical) this else 0f
    )

    @JvmName("velocityToFloat")
    private fun Velocity.toFloat() = if (orientation == Orientation.Horizontal) x else y

    @JvmName("offsetToFloat")
    private fun Offset.toFloat(): Float = if (orientation == Orientation.Horizontal) x else y
}

internal fun<T> Modifier.draggableAnchors(
    state: AnchoredDraggableState<T>,
    orientation: Orientation,
    anchors: (size: IntSize, constraints: Constraints) -> Pair<DraggableAnchors<T>, T>,
) = this then DraggableAnchorsElement(state, anchors, orientation)

private class DraggableAnchorsElement<T>(
    private val state: AnchoredDraggableState<T>,
    private val anchors: (size: IntSize, constraints: Constraints) -> Pair<DraggableAnchors<T>, T>,
    private val orientation: Orientation
) : ModifierNodeElement<DraggableAnchorsNode<T>>() {

    override fun create() = DraggableAnchorsNode(state, anchors, orientation)

    override fun update(node: DraggableAnchorsNode<T>) {
        node.state = state
        node.anchors = anchors
        node.orientation = orientation
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is DraggableAnchorsElement<*>) return false

        if (state != other.state) return false
        if (anchors !== other.anchors) return false
        if (orientation != other.orientation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + anchors.hashCode()
        result = 31 * result + orientation.hashCode()
        return result
    }

    override fun InspectorInfo.inspectableProperties() {
        debugInspectorInfo {
            properties["state"] = state
            properties["anchors"] = anchors
            properties["orientation"] = orientation
        }
    }
}

private class DraggableAnchorsNode<T>(
    var state: AnchoredDraggableState<T>,
    var anchors: (size: IntSize, constraints: Constraints) -> Pair<DraggableAnchors<T>, T>,
    var orientation: Orientation
) : Modifier.Node(), LayoutModifierNode {
    private var didLookahead: Boolean = false

    override fun onDetach() {
        didLookahead = false
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        // If we are in a lookahead pass, we only want to update the anchors here and not in
        // post-lookahead. If there is no lookahead happening (!isLookingAhead && !didLookahead),
        // update the anchors in the main pass.
        if (!isLookingAhead || !didLookahead) {
            val size = IntSize(placeable.width, placeable.height)
            val newAnchorResult = anchors(size, constraints)
            state.updateAnchors(newAnchorResult.first, newAnchorResult.second)
        }
        didLookahead = isLookingAhead || didLookahead
        return layout(placeable.width, placeable.height) {
            // In a lookahead pass, we use the position of the current target as this is where any
            // ongoing animations would move. If the component is in a settled state, lookahead
            // and post-lookahead will converge.
            val offset = if (isLookingAhead) {
                state.anchors.positionOf(state.targetValue)
            } else state.requireOffset()
            val xOffset = if (orientation == Orientation.Horizontal) offset else 0f
            val yOffset = if (orientation == Orientation.Vertical) offset else 0f
            placeable.place(xOffset.roundToInt(), yOffset.roundToInt())
        }
    }
}
