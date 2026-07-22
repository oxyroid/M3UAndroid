package com.m3u.smartphone

import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugExtensionPlatformEntryPoint {
    fun pluginRepository(): ExtensionPluginRepository
    fun providerRepository(): SubscriptionProviderRepository
    fun playlistRepository(): PlaylistRepository
}
