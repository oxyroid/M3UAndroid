package com.m3u.material.overscroll

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.google.android.material.math.MathUtils.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

internal class OverScrollNestedScrollConnection(
    private val state: OverScrollState,
    private val coroutineScope: CoroutineScope,
    private val scrollMultiplier: Float = 0.5f,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        return when {
            available.y < 0 -> onScroll(available)
            else -> Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        return when {
            available.y > 0 -> onScroll(available)
            else -> Offset.Zero
        }
    }

    private fun onScroll(available: Offset): Offset {
        state.inOverScroll = true
        val currentScrollMultiplier =
            lerp(scrollMultiplier, 0.01f, (available.y + state.offSet) / state.maxOffset)
        val newOffset = (available.y * currentScrollMultiplier + state.offSet).coerceAtLeast(0f)
        val consumed = newOffset - state.offSet
        return if (consumed.absoluteValue >= 0f && newOffset <= state.maxOffset) {
            coroutineScope.launch {
                state.dispatchOverScrollDelta(consumed)
            }
            // Return the consumed Y
            Offset(x = 0f, y = consumed / currentScrollMultiplier)
        } else {
            Offset.Zero
        }
    }


    override suspend fun onPreFling(available: Velocity): Velocity {
        state.inOverScroll = false
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        state.inOverScroll = false
        return Velocity.Zero
    }
}