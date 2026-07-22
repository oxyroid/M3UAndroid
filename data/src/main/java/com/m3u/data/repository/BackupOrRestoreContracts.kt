package com.m3u.data.repository

internal object BackupOrRestoreContracts {
    fun wrapPlaylist(encoded: String): String = "P,${encoded.trim()}"
    fun wrapChannel(encoded: String): String = "S,${encoded.trim()}"
    fun wrapProviderAccount(encoded: String): String = "A,${encoded.trim()}"
    fun wrapPlaybackReference(encoded: String): String = "R,${encoded.trim()}"
    fun unwrapPlaylist(wrapped: String): String? {
        val trimmed = wrapped.trim()
        if (!trimmed.startsWith("P,")) return null
        return trimmed.drop(2)
    }

    fun unwrapChannel(wrapped: String): String? {
        val trimmed = wrapped.trim()
        if (!trimmed.startsWith("S,")) return null
        return trimmed.drop(2)
    }

    fun unwrapProviderAccount(wrapped: String): String? = wrapped.unwrap("A,")
    fun unwrapPlaybackReference(wrapped: String): String? = wrapped.unwrap("R,")

    private fun String.unwrap(prefix: String): String? {
        val trimmed = trim()
        return trimmed.takeIf { it.startsWith(prefix) }?.drop(prefix.length)
    }
}
