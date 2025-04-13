package com.m3u.data.service.internal

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.jakewharton.disklrucache.DiskLruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

internal class ChannelPreferenceProvider(
    directory: File,
    appVersion: Int,
    ioDispatcher: CoroutineDispatcher
) {
    private val limitedParallelism = ioDispatcher.limitedParallelism(1, "channel-preference")
    private val cache = DiskLruCache.open(directory, appVersion, 3, 4 * 1024 * 1024) // 4mb

    suspend operator fun get(
        channelUrl: String
    ): ChannelPreference? = withContext(limitedParallelism) {
        runCatching {
            val key = encodeKey(channelUrl)
            val snapshot = cache.get(key) ?: return@withContext null
            snapshot.use {
                ChannelPreference(
                    cwPosition = it.getString(0)?.toLong() ?: -1L,
                    mineType = it.getString(1)?.takeIf { it.isNotEmpty() },
                    thumbnail = it.getString(2)?.toUri()
                )
            }
        }
            .onFailure {
                Log.e("TAG", "get: ", it)
            }
            .getOrNull()
    }

    suspend operator fun set(
        channelUrl: String,
        value: ChannelPreference
    ) = withContext(limitedParallelism) {
        runCatching {
            val key = encodeKey(channelUrl)
            val editor = cache.edit(key) ?: return@withContext
            editor.set(0, value.cwPosition.toString())
            editor.set(1, value.mineType.orEmpty())
            editor.set(2, value.thumbnail?.toString().orEmpty())
            editor.commit()
        }
            .onFailure {
                Log.e("TAG", "set: ", it)
            }
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
    val thumbnail: Uri? = null
)
