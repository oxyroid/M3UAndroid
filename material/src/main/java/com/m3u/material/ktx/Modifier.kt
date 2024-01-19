package com.m3u.material.ktx

import androidx.compose.ui.Modifier

fun Modifier.thenIf(condition: Boolean, factory: () -> Modifier): Modifier = then(
    if (condition) factory() else Modifier
)