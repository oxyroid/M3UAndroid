package com.m3u.smartphone.ui.common.helper

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.smartphone.ui.material.components.Destination

@Immutable
data class Action(
    val icon: ImageVector,
    val contentDescription: String?,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Immutable
data class Fob(
    val destination: Destination,
    val icon: ImageVector,
    @StringRes val iconTextId: Int,
    val onClick: () -> Unit
)
