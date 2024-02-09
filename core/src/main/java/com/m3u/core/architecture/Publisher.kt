package com.m3u.core.architecture

interface Publisher {
    val author: String get() = "realOxy"
    val repository: String get() = "M3UAndroid"
    val applicationId: String
    val versionName: String
    val versionCode: Int
    val debug: Boolean
    val snapshot: Boolean
    val model: String
    val isTelevision: Boolean
}
