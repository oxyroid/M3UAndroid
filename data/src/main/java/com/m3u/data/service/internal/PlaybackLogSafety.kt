package com.m3u.data.service.internal

import com.m3u.data.service.MediaCommand
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Produces a diagnostic value without expanding media metadata or playback references.
 *
 * Provider item identifiers and episode artwork are extension-controlled strings, so even a
 * command that does not directly own a playback URL must not rely on its generated [toString].
 */
internal fun MediaCommand?.toPlaybackLogSummary(): String = when (this) {
    is MediaCommand.Common -> "Common[channelId=$channelId]"
    is MediaCommand.Episode -> "Episode[channelId=$channelId]"
    null -> "None"
}

/**
 * Keeps the useful chain state and MIME candidate while deliberately accepting no URL argument.
 */
internal fun playbackChainLogSummary(
    state: String,
    mimeType: String? = null,
): String = buildString {
    append(state)
    mimeType?.let {
        append("[mimeType=")
        append(it)
        append(']')
    }
}

/**
 * Copies throwable structure and stack frames without copying messages or custom throwable data.
 *
 * Network and Media3 exception messages routinely contain the request URL. Provider failures may
 * also contain authorization headers or extension-controlled details. Logging the original
 * throwable would therefore bypass message-level redaction at the call site.
 */
internal fun Throwable.toPlaybackLogThrowable(): Throwable {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    return toPlaybackLogThrowable(
        seen = seen,
        depth = 0,
    )
}

private fun Throwable.toPlaybackLogThrowable(
    seen: MutableSet<Throwable>,
    depth: Int,
): Throwable {
    val typeName = javaClass.name
    if (depth >= MAX_THROWABLE_DEPTH || !seen.add(this)) {
        return PlaybackLogThrowable("$typeName (details omitted)")
    }
    return PlaybackLogThrowable(typeName).also { safe ->
        safe.stackTrace = stackTrace
        cause?.let { originalCause ->
            safe.initCause(
                originalCause.toPlaybackLogThrowable(
                    seen = seen,
                    depth = depth + 1,
                )
            )
        }
        suppressed
            .take(MAX_SUPPRESSED_THROWABLES)
            .forEach { originalSuppressed ->
                safe.addSuppressed(
                    originalSuppressed.toPlaybackLogThrowable(
                        seen = seen,
                        depth = depth + 1,
                    )
                )
            }
    }
}

private class PlaybackLogThrowable(
    originalTypeName: String,
) : RuntimeException(originalTypeName)

private const val MAX_THROWABLE_DEPTH = 8
private const val MAX_SUPPRESSED_THROWABLES = 8
