package com.m3u.data.extension

import com.m3u.data.extension.emby.EmbyCompatibleClient
import com.m3u.data.extension.emby.EmbyCompatibleProvider
import com.m3u.data.extension.emby.OkHttpEmbyCompatibleClient
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.repository.provider.SubscriptionProviderRepositoryImpl
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ExtensionBindingsModule {
    @Binds
    @Singleton
    abstract fun bindEmbyCompatibleClient(
        client: OkHttpEmbyCompatibleClient,
    ): EmbyCompatibleClient

    @Binds
    @Singleton
    abstract fun bindSubscriptionProviderRepository(
        repository: SubscriptionProviderRepositoryImpl,
    ): SubscriptionProviderRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object ExtensionRuntimeModule {
    @Provides
    @Singleton
    fun provideExtensionRuntime(provider: EmbyCompatibleProvider): ExtensionRuntime = ExtensionRuntime(
        hostApiVersion = ExtensionApiVersions.Current
    ).apply {
        val registration = register(provider)
        check(registration is ExtensionRegistrationResult.Registered) {
            "Failed to register built-in Emby-compatible provider"
        }
    }
}
