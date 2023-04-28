package com.m3u.core.architecture

interface Publisher {
    val applicationID: String
    val versionName: String
    val debug: Boolean
    val maxTabIndex: Int
    fun getTabTitle(index: Int): String
}