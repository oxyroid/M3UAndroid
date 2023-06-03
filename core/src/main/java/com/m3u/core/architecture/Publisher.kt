package com.m3u.core.architecture

interface Publisher {
    val applicationID: String
    val versionName: String
    val debug: Boolean
    val destinationsCount: Int
    fun getDestination(index: Int): String
}