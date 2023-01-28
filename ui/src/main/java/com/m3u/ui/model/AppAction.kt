package com.m3u.ui.model

import com.m3u.core.icon.Icon

data class AppAction(
    val icon: Icon,
    val contentDescription: String?,
    val onClick: () -> Unit
)