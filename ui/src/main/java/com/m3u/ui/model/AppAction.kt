package com.m3u.ui.model

data class AppAction(
    val icon: Icon,
    val contentDescription: String?,
    val onClick: () -> Unit
)

typealias SetActions = (List<AppAction>) -> Unit