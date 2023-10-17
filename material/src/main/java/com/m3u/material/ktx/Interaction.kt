package com.m3u.material.ktx

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

enum class InteractionType {
    PRESS, DRAG, FOCUS, HOVER
}

@Composable
fun InteractionType.visibleIn(source: InteractionSource): Boolean {
    val state by when (this) {
        InteractionType.PRESS -> source.collectIsPressedAsState()
        InteractionType.DRAG -> source.collectIsDraggedAsState()
        InteractionType.FOCUS -> source.collectIsFocusedAsState()
        InteractionType.HOVER -> source.collectIsHoveredAsState()
    }
    return state
}
