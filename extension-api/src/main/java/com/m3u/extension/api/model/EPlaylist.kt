package com.m3u.extension.api.model

data class EPlaylist(
    val url: String,
    val title: String,
    val userAgent: String,
    val dataSource: String,
) : EMedia
