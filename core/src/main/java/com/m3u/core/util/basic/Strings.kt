@file:Suppress("unused")

package com.m3u.core.util.basic

fun String.trimBrackets(): String {
    return if (this.isEmpty()) this
    else if (this.length > 1 && this.firstOrNull() == '\"' && this.lastOrNull() == '\"') {
        this.substring(1 until this.lastIndex)
    } else this
}

inline fun <reified C : Any> createClazzKey(): String {
    return C::class.java.name
}

fun String.uppercaseLetter(): String {
    if (this.isEmpty()) return this
    val split = this.split(" ")
    return split.joinToString(
        separator = " ",
        transform = { it.first().uppercase() + it.drop(1) }
    )
}

fun String.uppercaseFirst(): String {
    if (this.isEmpty()) return this
    return this.first().uppercase() + this.drop(1)
}

fun String.splitOutOfQuotation(delimiter: Char): List<String> {
    val list = mutableListOf<String>()
    var start = 0
    var inQuotes = false
    var quoteChar: Char? = null

    for (i in indices) {
        val c = this[i]
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
