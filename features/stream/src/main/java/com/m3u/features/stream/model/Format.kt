package com.m3u.features.stream.model

import androidx.compose.runtime.Immutable
import androidx.media3.common.Format as Media3Format

@Immutable
data class Format(
    val id: String,
    val width: Int,
    val height: Int,
    val codecs: String
)

fun Media3Format.asFormat(): Format? {
    return Format(
        id = id ?: return null,
        width = width,
        height = height,
        codecs = codecs ?: return null
    )
}