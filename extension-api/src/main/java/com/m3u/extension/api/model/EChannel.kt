package com.m3u.extension.api.model

data class EChannel(
    val name: String,
    val url: String,
    val category: String,
    val cover: String,
    val playlistUrl: String,
    val licenseType: String,
    val licenseKey: String
): EMedia
