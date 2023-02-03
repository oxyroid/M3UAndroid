package com.m3u.core.architecture

interface PackageProvider {
    fun getApplicationID(): String
    fun getVersionName(): String
    fun isDebug(): Boolean
}