package com.m3u.data.work

object BackupContracts {
    fun wrapPlaylist(encoded: String): String = "P,$encoded"
    fun wrapStream(encoded: String): String = "S,$encoded"
    fun unwrapPlaylist(wrapped: String): String? {
        if (!wrapped.startsWith("P,")) return null
        return wrapped.drop(2)
    }
    fun unwrapStream(wrapped: String): String? {
        if (!wrapped.startsWith("S,")) return null
        return wrapped.drop(2)
    }
}