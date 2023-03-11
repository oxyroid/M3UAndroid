package com.m3u.ui.shared.animation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.m3u.ui.shared.SharedState
import com.m3u.ui.shared.material.SharedSurface
import com.m3u.ui.shared.sharedElement

/**
 * Providing an animated modifier to the content with the changing of [SharedState].
 * @param state Target [SharedState].
 * @param content The content which holding an animated modifier.
 * @see SharedState
 * @see SharedSurface
 */
@Composable
@Deprecated("Use animateSharedState, Modifier.shared and Modifier.onShared instead")
fun AnimatedModifier(
    state: SharedState = LocalConfiguration.current.sharedElement,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val animatedSharedState by animateSharedElementState(state)
    val combinedModifier = modifier.sharedElement(
        state = animatedSharedState
    )
    content(combinedModifier)
}
