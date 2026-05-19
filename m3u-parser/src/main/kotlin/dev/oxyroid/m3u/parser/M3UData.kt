package dev.oxyroid.m3u.parser

data class M3UData(
    val id: String = "",
    val name: String = "",
    val cover: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = "",
    val duration: Double = -1.0,
    val licenseType: String? = null,
    val licenseKey: String? = null,
)
