package com.m3u.core.architecture

import javax.inject.Qualifier

interface Publisher {
    val applicationID: String
    val versionName: String
    val debug: Boolean
    val destinationsCount: Int
    fun getDestination(index: Int): String

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class App
}