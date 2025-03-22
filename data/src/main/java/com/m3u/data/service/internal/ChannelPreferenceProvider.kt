package com.m3u.data.service.internal

import com.jakewharton.disklrucache.DiskLruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

internal class ChannelPreferenceProvider(
    directory: File,
    appVersion: Int,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val cache = DiskLruCache.open(directory, appVersion, 2, 4 * 1024 * 1024) // 4mb

    suspend operator fun get(channelUrl: String): ChannelPreference? = withContext(ioDispatcher) {
        val key = encodeKey(channelUrl)
        val snapshot = cache.get(key) ?: return@withContext null
        ChannelPreference(
            cwPosition = snapshot.getString(0).toLong(),
            mineType = snapshot.getString(1)
        )
    }
    suspend operator fun set(channelUrl: String, value: ChannelPreference) = withContext(ioDispatcher) {
        val key = encodeKey(channelUrl)
        val editor = cache.edit(key) ?: return@withContext
        editor.set(0, value.cwPosition.toString())
        editor.set(1, value.mineType)
        editor.commit()
    }

    // [a-z0-9_-]{1,64}
    private fun encodeKey(channelUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(channelUrl.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * @param cwPosition the continue watching position of the channel.
 * @param mineType the mine type of the channel.
 */
internal data class ChannelPreference(
    val cwPosition: Long = -1L,
    val mineType: String? = null,
)
