package com.m3u.core.architecture

import javax.inject.Qualifier

interface Publisher {
    val author: String get() = "realOxy"
    val repository: String get() = "M3UAndroid"
    val applicationID: String
    val versionName: String
    val versionCode: Int
    val debug: Boolean

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class App
}
