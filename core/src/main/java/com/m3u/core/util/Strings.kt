package com.m3u.core.util

fun String.trimBrackets(): String {
    return if (this.isEmpty()) this
    else if (this.length > 1 && this.firstOrNull() == '\"' && this.lastOrNull() == '\"') {
        this.substring(1 until this.lastIndex)
    } else this
}