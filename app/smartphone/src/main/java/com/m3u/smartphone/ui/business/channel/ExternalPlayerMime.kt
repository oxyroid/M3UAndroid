package com.m3u.smartphone.ui.business.channel

internal fun externalPlayerMimeType(url: String): String {
    return when (url.streamExtension()) {
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "oga",
        "ogg",
        "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "weba" -> "audio/webm"
        else -> "video/*"
    }
}

private fun String.streamExtension(): String {
    val path = substringBefore('#')
        .substringBefore('?')
        .trimEnd('/')
    return path.substringAfterLast('/', missingDelimiterValue = path)
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
}
