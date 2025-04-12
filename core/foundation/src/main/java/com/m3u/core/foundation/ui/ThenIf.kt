package com.m3u.core.foundation.ui

import androidx.compose.ui.Modifier

inline fun Modifier.thenIf(condition: Boolean, factory: () -> Modifier): Modifier = then(
    if (condition) factory() else Modifier
)

inline fun <T> Modifier.notNull(key: T?, factory: (T) -> Modifier): Modifier = then(
    if (key != null) factory(key) else Modifier
)
