package com.m3u.data.extension

import com.m3u.core.extension.ExtensionChannelStore
import com.m3u.core.extension.ExtensionPlaylistStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ExtensionDataModule {
    @Binds
    @Singleton
    fun bindExtensionPlaylistStore(
        store: RoomExtensionPlaylistStore
    ): ExtensionPlaylistStore

    @Binds
    @Singleton
    fun bindExtensionChannelStore(
        store: RoomExtensionChannelStore
    ): ExtensionChannelStore
}
