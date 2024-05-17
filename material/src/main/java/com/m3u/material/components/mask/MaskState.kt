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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

@Stable
interface MaskState {
    val visible: Boolean
    val locked: Boolean
    fun wake()
    fun sleep()
    fun lock(key: Any)
    fun unlock(key: Any, delay: Duration = Duration.ZERO)
    fun unlockAll(delay: Duration = Duration.ZERO)
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
    private var keys by mutableStateOf<Set<Any>>(emptySet())
    override val locked: Boolean by derivedStateOf { keys.isNotEmpty() }

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
        val iterator = unlockedJobs.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            unlockImpl(entity.key)
            entity.value.cancel()
        }
    }

    override fun lock(key: Any) {
        unlockedJobs.remove(key)?.cancel()
        keys += key
    }

    @Volatile
    private var unlockedJobs = mutableMapOf<Any, Job>()

    override fun unlock(key: Any, delay: Duration) {
        unlockedJobs[key] = launch {
            delay(delay)
            if (key in unlockedJobs) {
                unlockImpl(key)
            }
            unlockedJobs.remove(key)
        }
    }

    override fun unlockAll(delay: Duration) {
        keys.map { key ->
            unlockedJobs[key] = launch {
                delay(delay)
                if (key in unlockedJobs) {
                    unlockImpl(key)
                }
                unlockedJobs.remove(key)
            }
        }
    }

    private fun unlockImpl(key: Any) {
        keys -= key
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
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MaskState {
    return remember(minDuration, coroutineScope) {
        MaskStateCoroutineImpl(
            minDuration = minDuration,
            coroutineScope = coroutineScope,
        )
    }
}
