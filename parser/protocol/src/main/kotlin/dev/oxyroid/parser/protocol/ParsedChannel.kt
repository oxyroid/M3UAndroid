package dev.oxyroid.parser.protocol

data class ParsedChannel(
    val id: String = "",
    val name: String = "",
    val cover: String = "",
    val group: String = "",
    val title: String = "",
    val url: String = "",
    val duration: Double = -1.0,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val options: Map<String, String> = emptyMap(),
)
