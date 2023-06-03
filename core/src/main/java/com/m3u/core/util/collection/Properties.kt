package com.m3u.core.util.collection

import java.util.Properties

fun Properties.loadLine(line: String) {
    val trim = line.trim()
    val index = trim.indexOf("=")
    if (index != -1) {
        val key = trim.take(index)
        val value = trim.drop(index + 1)
        this.setProperty(key.trim(), value.trim())
    }
}