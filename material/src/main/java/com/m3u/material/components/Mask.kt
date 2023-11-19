package com.m3u.material.components

import androidx.annotation.IntRange
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.m3u.material.ktx.animated
import com.m3u.material.ktx.ifUnspecified
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
interface MaskState {
    val visible: Boolean
    fun touch()
    fun active()
    fun sleep()
    fun lock()
    fun unlock()
}

private class MaskStateCoroutineImpl(
    @IntRange(from = 1) private val minDuration: Long = MaskDefaults.minDuration,
    coroutineScope: CoroutineScope,
    private val onChanged: (Boolean) -> Unit
) : MaskState {
    private var currentTime: Long by mutableLongStateOf(systemClock)
    private var lastTime: Long by mutableLongStateOf(0L)
    private var locked: Boolean by mutableStateOf(false)

    private var last: Boolean? = null
    override val visible: Boolean
        get() = locked || (currentTime - lastTime <= minDuration).also {
            if (it != last) {
                if (last != null) {
                    onChanged(it)
                }
                last = it
            }
        }

    init {
        if (minDuration < 1L) error("minSecondDuration cannot less than 1s.")
        coroutineScope.launch {
            while (true) {
                delay(1000L)
                currentTime += 1
            }
        }
    }

    private val systemClock: Long get() = System.currentTimeMillis() / 1000

    override fun touch() {
        lastTime = if (!visible) currentTime else 0
    }

    override fun active() {
        lastTime = currentTime
    }

    override fun sleep() {
        lastTime = 0
    }

    override fun lock() {
        locked = true
    }

    override fun unlock() {
        locked = false
    }
}

@Composable
fun rememberMaskState(
    @IntRange(from = 1) minDuration: Long = MaskDefaults.minDuration,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    onChanged: (Boolean) -> Unit
): MaskState {
    val currentOnChanged by rememberUpdatedState(onChanged)
    return remember(minDuration, coroutineScope) {
        MaskStateCoroutineImpl(
            minDuration = minDuration,
            coroutineScope = coroutineScope,
            onChanged = currentOnChanged
        )
    }
}

@Composable
fun Mask(
    state: MaskState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = LocalContentColor.current,
    content: @Composable BoxScope.() -> Unit
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            OuterBox(
                modifier = modifier
                    .background(backgroundColor)
                    .statusBarsPadding(),
                content = content
            )
        }
    }
}

@Composable
fun MaskPanel(
    state: MaskState,
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
                onClick = state::touch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        content = content
    )
}

@Composable
fun MaskButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true
) {
    val tooltipState = rememberTooltipState()

    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            state.lock()
        } else {
            state.unlock()
        }
    }

    val animatedColor by tint.ifUnspecified {
        LocalContentColor.current
    }
        .animated("MaskButtonTint")

    val currentKeepAlive by rememberUpdatedState(state::active)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = contentDescription.uppercase())
            }
        },
        state = tooltipState
    ) {
        IconButton(
            icon = icon,
            enabled = enabled,
            contentDescription = null,
            onClick = {
                currentKeepAlive()
                onClick()
            },
            modifier = modifier,
            tint = animatedColor
        )
    }
}

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
            state.active()
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

object MaskDefaults {
    const val minDuration = 4L
}
