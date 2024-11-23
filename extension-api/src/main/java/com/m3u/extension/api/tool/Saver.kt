package com.m3u.extension.api.tool

import com.m3u.extension.api.model.EChannel
import com.m3u.extension.api.model.EPlaylist
import com.m3u.extension.api.workflow.Workflow

/**
 * Extensions don't need to implement this interface,
 * just pass it into your [Workflow] primary constructor.
 *
 * EPG playlists and programmes are not supported for now.
 */
interface Saver {
    /**
     * @return if false. Means failed, cancelled, or reject by user.
     */
    suspend fun savePlaylist(playlist: EPlaylist): Boolean
    suspend fun saveChannel(channel: EChannel): Boolean
}