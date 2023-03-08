package com.m3u.features.crash.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.m3u.ui.shared.SharedState
import com.m3u.ui.shared.SharedScope
import com.m3u.ui.shared.sharedState

internal sealed class Destination : SharedScope {
    object List : Destination() {
        override val isElement: Boolean = false
        override val state: SharedState
            @Composable get() = LocalConfiguration.current.sharedState

        @Suppress("UNCHECKED_CAST")
        override fun <T : SharedScope> copy(state: SharedState): T = this as T
    }

    data class Detail(
        override val state: SharedState,
        val path: String
    ) : Destination() {
        override val isElement: Boolean = true

        @Suppress("UNCHECKED_CAST")
        override fun <T : SharedScope> copy(state: SharedState): T = copy(
            state = state,
            path = path
        ) as T
    }
}
