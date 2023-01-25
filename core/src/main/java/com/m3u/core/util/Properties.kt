package com.m3u.core.util

import java.util.*

fun Properties.loadLine(line: String) {
    val trim = line.trim()
    val index = trim.indexOf("=")
    if (index != -1) {
        val key = trim.take(index).ifEmpty { error("Cannot found key: $line") }
        val value = trim.drop(index + 1)
        this.setProperty(key, value)
    } else {
        error("Cannot parse this to a property line: $line")
    }
}