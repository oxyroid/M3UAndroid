package com.m3u.ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.m3u.ui.shared.material.SharedSurface

/**
 * An wrapper for classes which hold a [SharedState] without needing field injection.
 *
 * @see SharedSurface
 */
@Immutable
@Deprecated("Use animateSharedState, Modifier.shared and Modifier.onShared instead")
interface SharedScope {
    @get:Composable
    val state: SharedState

    /**
     * Copy a holding subclass extra properties instance.
     *
     * @suppress [T] can only be itself.
     */
    @Composable
    fun copy(
        state: SharedState = this.state,
    ): SharedScope {
        error("Copy instance in this SharedScope is not allowed!")
    }
}

@Deprecated("Use animateSharedState, Modifier.shared and Modifier.onShared instead")
interface SharedElementScope : SharedScope {
    fun expand()
    fun shrink()

    @Composable
    override fun copy(state: SharedState): SharedElementScope
}