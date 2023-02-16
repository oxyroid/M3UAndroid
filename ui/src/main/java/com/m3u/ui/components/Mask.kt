package com.m3u.ui.components

import androidx.annotation.IntRange
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.m3u.ui.model.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface MaskState {
    val visible: Boolean
    fun excite()
    fun keepAlive()
    fun fail()
}

@Stable
class MaskStateCoroutineImpl(
    @IntRange(from = 1000) private val minDuration: Long = 2500L,
    coroutineScope: CoroutineScope
) : MaskState {
    private var currentTime: Long by mutableStateOf(System.currentTimeMillis())
    private var lastTime: Long by mutableStateOf(0L)

    override val visible: Boolean get() = (currentTime - lastTime <= minDuration)

    init {
        if (minDuration < 1000L) error("minDuration cannot less than 1000ms.")
        coroutineScope.launch {
            while (true) {
                delay(1000L)
                currentTime += 1000L
            }
        }
    }

    override fun excite() {
        lastTime = if (!visible) System.currentTimeMillis() else 0
    }

    override fun keepAlive() {
        lastTime = System.currentTimeMillis()
    }

    override fun fail() {
        lastTime = 0
    }
}

@Composable
fun rememberMaskState(
    @IntRange(from = 1000) minDuration: Long = 2500L,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): MaskStateCoroutineImpl {
    return remember(minDuration, coroutineScope) {
        MaskStateCoroutineImpl(
            minDuration = minDuration,
            coroutineScope = coroutineScope
        )
    }
}

@Composable
fun Mask(
    state: MaskStateCoroutineImpl,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = LocalContentColor.current,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            OuterColumn(
                modifier = modifier.background(backgroundColor),
                content = content,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment
            )
        }
    }
}

@Composable
fun MaskPanel(
    state: MaskStateCoroutineImpl,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    OuterColumn(
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
            .fillMaxSize()
            .clickable(
                onClick = state::excite,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .draggable(
                state = rememberDraggableState {

                },
                orientation = Orientation.Vertical
            ),
        content = content
    )
}

@Composable
fun MaskButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        icon = Icon.ImageVectorIcon(icon),
        contentDescription = null,
        onClick = {
            state.keepAlive()
            onClick()
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MaskCircleButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Surface(
        shape = CircleShape,
        onClick = {
            state.keepAlive()
            onClick()
        },
        modifier = modifier,
        color = Color.Unspecified
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = tint
        )
    }
}