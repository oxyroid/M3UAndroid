package com.m3u.core.architecture

interface Packager {
    val applicationID: String
    val versionName: String
    val debug: Boolean
}