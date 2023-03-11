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