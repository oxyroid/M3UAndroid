package com.m3u.core.util.transform

interface Transferable<T> {
    fun transfer(src: T): String
    fun accept(dest: String): T
}