package com.m3u.data.repository

internal object BackupOrRestoreContracts {
    fun wrapPlaylist(encoded: String): String = "P,${encoded.trim()}"
    fun wrapStream(encoded: String): String = "S,${encoded.trim()}"
    fun unwrapPlaylist(wrapped: String): String? {
        val trimmed = wrapped.trim()
        if (!trimmed.startsWith("P,")) return null
        return trimmed.drop(2)
    }

    fun unwrapStream(wrapped: String): String? {
        val trimmed = wrapped.trim()
        if (!trimmed.startsWith("S,")) return null
        return trimmed.drop(2)
    }
}