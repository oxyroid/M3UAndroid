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