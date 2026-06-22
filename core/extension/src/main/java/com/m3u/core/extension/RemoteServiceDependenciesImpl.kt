package com.m3u.core.extension

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class RemoteServiceDependenciesImpl : RemoteServiceDependencies {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EntryPoint {
        val extensionPlaylistStore: ExtensionPlaylistStore
        val extensionChannelStore: ExtensionChannelStore
    }

    private val entryPoint: EntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            Utils.getContext(),
            EntryPoint::class.java
        )
    }

    override val playlistStore: ExtensionPlaylistStore = entryPoint.extensionPlaylistStore
    override val channelStore: ExtensionChannelStore = entryPoint.extensionChannelStore
}
