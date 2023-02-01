package com.m3u.core.architecture

interface PackageProvider {
    fun getName(): String
    fun version(): String
    fun isDebug(): Boolean
}