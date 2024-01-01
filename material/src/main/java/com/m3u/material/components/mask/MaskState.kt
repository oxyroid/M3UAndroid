package com.m3u.material.components.mask

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

@Stable
interface MaskState {
    val visible: Boolean
    fun wake()
    fun sleep()
    fun lock()
    fun unlock(delay: Duration = Duration.ZERO)
    fun intercept(interceptor: MaskInterceptor?)
}

fun MaskState.toggle() {
    if (!visible) wake() else sleep()
}

private class MaskStateCoroutineImpl(
    @IntRange(from = 1) private val minDuration: Long = MaskDefaults.MIN_DURATION_SECOND,
    coroutineScope: CoroutineScope
) : MaskState, CoroutineScope by coroutineScope {
    private var currentTime: Long by mutableLongStateOf(systemClock)
    private var lastTime: Long by mutableLongStateOf(0L)
    private var locked: Boolean by mutableStateOf(false)

    override val visible: Boolean by derivedStateOf {
        val before = (locked || (currentTime - lastTime <= minDuration))
        interceptor?.invoke(before) ?: before
    }

    init {
        if (minDuration < 1L) error("minSecondDuration cannot less than 1s.")
        launch {
            while (true) {
                delay(1000L)
                currentTime += 1
            }
        }
    }

    private val systemClock: Long get() = System.currentTimeMillis() / 1000

    override fun wake() {
        lastTime = currentTime
    }

    override fun sleep() {
        lastTime = 0
    }

    override fun lock() {
        locked = true
    }

    override fun unlock(delay: Duration) {
        launch {
            delay(delay)
            locked = false
        }
    }

    @Volatile
    private var interceptor: MaskInterceptor? = null

    override fun intercept(interceptor: MaskInterceptor?) {
        this.interceptor = interceptor
    }
}

@Composable
fun rememberMaskState(
    @IntRange(from = 1) minDuration: Long = MaskDefaults.MIN_DURATION_SECOND,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): MaskState {
    return remember(minDuration, coroutineScope) {
        MaskStateCoroutineImpl(
            minDuration = minDuration,
            coroutineScope = coroutineScope
        )
    }
}
