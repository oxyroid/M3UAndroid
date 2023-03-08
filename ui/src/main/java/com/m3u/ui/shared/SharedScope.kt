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
interface SharedScope {

    val isElement: Boolean

    @get:Composable
    val state: SharedState

    /**
     * Copy a holding subclass extra properties instance.
     *
     * @suppress [T] can only be the subclass.
     */
    fun <T : SharedScope> copy(state: SharedState): T
}