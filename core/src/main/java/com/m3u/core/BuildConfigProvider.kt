package com.m3u.core

interface BuildConfigProvider {
    fun getName(): String
    fun version(): String
    fun isDebug(): Boolean
}