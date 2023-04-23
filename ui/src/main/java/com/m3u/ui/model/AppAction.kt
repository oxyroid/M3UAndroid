package com.m3u.ui.model

import androidx.compose.ui.graphics.vector.ImageVector

data class AppAction(
    val icon: ImageVector,
    val contentDescription: String?,
    val onClick: () -> Unit
)