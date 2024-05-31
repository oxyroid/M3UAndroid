@file:Suppress("unused")

package com.m3u.core.util.basic

import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale

fun String.title(): String {
    if (this.isEmpty()) return this
    val split = this.split(" ")
    return split.joinToString(
        separator = " ",
        transform = { it.capitalize(Locale.current) }
    )
}

fun String.startsWithAny(vararg prefix: String, ignoreCase: Boolean = false): Boolean {
    return prefix.any { startsWith(it, ignoreCase) }
}

fun String.startWithHttpScheme(): Boolean = startsWithAny(
    "http://", "https://",
    ignoreCase = true
)