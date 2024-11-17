package com.m3u.material.overscroll

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

enum class OverScrollEffect {
    Scale,
    ScaleCenter,
    ParallaxVertical,
    ParallaxHorizontal,
    Alpha,
    RotationCenter,
    RotationVertical,
    RotationHorizontal,
    Header,
}

@Stable
class OverScrollState(
    val maxOffset: Float = 400f,
    private val animationSpec: AnimationSpec<Float> = SpringSpec(),
) {

    private val _overScrollOffset = Animatable(0f)
    private val mutatorMutex = MutatorMutex()

    val offSet: Float get() = _overScrollOffset.value

    val progress: Float get() = offSet / maxOffset

    var inOverScroll: Boolean by mutableStateOf(false)
        internal set

    internal suspend fun animateOffsetTo(offset: Float) {
        mutatorMutex.mutate {
            _overScrollOffset.animateTo(offset, animationSpec)
        }
    }

    internal suspend fun dispatchOverScrollDelta(delta: Float) {
        mutatorMutex.mutate(MutatePriority.UserInput) {
            _overScrollOffset.snapTo(_overScrollOffset.value + delta)
        }
    }
}

val LocalOverScrollState = staticCompositionLocalOf<OverScrollState> { error("Please provide OverScrollState") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverScroll(
    modifier: Modifier = Modifier,
    maxOffset: Float = 400f,
    animationSpec: AnimationSpec<Float> = SpringSpec(),
    content: @Composable () -> Unit,
) {
    val state: OverScrollState = remember {
        OverScrollState(maxOffset, animationSpec)
    }
    val coroutineScope = rememberCoroutineScope()
    val nestedScrollConnection = OverScrollNestedScrollConnection(state, coroutineScope)
    LaunchedEffect(state.inOverScroll) {
        if (!state.inOverScroll) {
            // If there's not a swipe in progress, rest the indicator at 0f
            state.animateOffsetTo(0f)
        }
    }

    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null,
        LocalOverScrollState provides state
    ) {
        Column(modifier.nestedScroll(connection = nestedScrollConnection)) {
            content()
        }
    }
}