package com.m3u.data.parser.xtream

data class XtreamOutput(
    val liveCategories: List<XtreamCategory> = emptyList(),
    val vodCategories: List<XtreamCategory> = emptyList(),
    val serialCategories: List<XtreamCategory> = emptyList(),
    val allowedOutputFormats: List<String> = emptyList(),
    val serverProtocol: String = "http",
    val port: Int? = null
)
