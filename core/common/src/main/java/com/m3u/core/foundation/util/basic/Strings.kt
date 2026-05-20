@file:Suppress("unused")
package com.m3u.core.foundation.util.basic

import java.util.Locale

fun String.title(): String {
    if (this.isEmpty()) return this
    val split = this.split(" ")
    return split.joinToString(
        separator = " ",
        transform = { value ->
            value.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    )
}

fun String.startsWithAny(vararg prefix: String, ignoreCase: Boolean = false): Boolean {
    return prefix.any { startsWith(it, ignoreCase) }
}

fun String.startWithHttpScheme(): Boolean = startsWithAny(
    "http://", "https://",
    ignoreCase = true
)
