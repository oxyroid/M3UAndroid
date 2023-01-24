package com.m3u.core.util

import java.util.*

fun Properties.loadLine(line: String) {
    val trim = line.trim()
    val index = trim.indexOf("=")
    val key = trim.take(index).ifEmpty { error("Cannot found key: $line") }
    val value = trim.drop(index + 1)
    this.setProperty(key, value)
}