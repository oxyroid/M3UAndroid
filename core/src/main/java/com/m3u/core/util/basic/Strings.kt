@file:Suppress("unused")

package com.m3u.core.util.basic

import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale

fun String.trimBrackets(): String {
    return if (this.isEmpty()) this
    else if (this.length > 1 && this.firstOrNull() == '\"' && this.lastOrNull() == '\"') {
        this.substring(1 until this.lastIndex)
    } else this
}

fun String.title(): String {
    if (this.isEmpty()) return this
    val split = this.split(" ")
    return split.joinToString(
        separator = " ",
        transform = { it.capitalize(Locale.current) }
    )
}

fun String.loopOutOfQuotation(block: (Char) -> Unit) {
    var flag = false
    this.forEach {
        if (it == '\"' || it == '\'') flag = !flag
        if (!flag) block(it)
    }
}

fun String.splitOutOfQuotation(delimiter: Char): List<String> {
    val list = mutableListOf<String>()
    var start = 0
    var inQuotes = false
    var quoteChar: Char? = null
    forEachIndexed { i, c ->
        if (c == '\'' || c == '\"') {
            if (!inQuotes) {
                inQuotes = true
                quoteChar = c
            } else if (quoteChar == c) {
                inQuotes = false
                quoteChar = null
            }
        }
        if (c == delimiter && !inQuotes) {
            list.add(substring(start, i))
            start = i + 1
        }
    }
    list.add(substring(start))
    return list
}

fun String.startsWithAny(vararg prefix: String): Boolean {
    prefix.forEach {
        if (startsWith(it)) return true
    }
    return false
}