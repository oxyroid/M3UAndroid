package com.m3u.data.service.player

import android.graphics.Rect
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoSize

internal fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

internal sealed class MimetypeIterator {
    class Unspecified(val url: String) : MimetypeIterator()
    class Trying(val mimeType: String) : MimetypeIterator()
    object Unsupported : MimetypeIterator()

    val mimeTypeOrNull: String?
        get() = when (this) {
            is Trying -> mimeType
            else -> null
        }

    companion object {
        val ORDER_DEFAULT = arrayOf(
            MimeTypes.APPLICATION_SS,
            MimeTypes.APPLICATION_M3U8,
            MimeTypes.APPLICATION_MPD,
            MimeTypes.APPLICATION_RTSP
        )
    }

    override fun toString(): String = when (this) {
        is Trying -> "Trying[$mimeType]"
        is Unspecified -> "Unspecified[$url]"
        Unsupported -> "Unsupported"
    }

    operator fun hasNext(): Boolean = this != Unsupported

    operator fun next(): MimetypeIterator = when (this) {
        is Unspecified -> Trying(ORDER_DEFAULT.first())
        is Trying -> {
            ORDER_DEFAULT
                .getOrNull(ORDER_DEFAULT.indexOf(mimeType) + 1)
                ?.let { Trying(it) }
                ?: Unsupported
        }

        else -> throw IllegalArgumentException()
    }
}