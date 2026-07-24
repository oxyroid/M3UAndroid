package com.m3u.data.extension

import android.content.Context
import com.m3u.data.extension.emby.EmbyCompatibleClient
import com.m3u.data.extension.emby.EmbyCompatibleProvider
import com.m3u.data.extension.emby.OkHttpEmbyCompatibleClient
import com.m3u.data.extension.security.AndroidKeystoreCredentialVault
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.extension.security.ExtensionSecretStore
import com.m3u.data.extension.security.ExtensionHookBrokerScopeProvider
import com.m3u.data.extension.security.HostNetworkBrokerImpl
import com.m3u.data.extension.security.ProviderHostNetworkBroker
import com.m3u.data.repository.extension.ExtensionContributionRepository
import com.m3u.data.repository.extension.ExtensionContributionRepositoryImpl
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.extension.ExtensionSettingStore
import com.m3u.data.repository.extension.ExtensionSettingsRepository
import com.m3u.data.repository.extension.ExtensionSettingsRepositoryImpl
import com.m3u.data.repository.extension.WorkManagerExtensionContributionScheduler
import com.m3u.data.repository.plugin.AndroidExtensionPluginDiscovery
import com.m3u.data.repository.plugin.AndroidExtensionPluginTransportConnector
import com.m3u.data.repository.plugin.ExtensionPluginDiscovery
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepositoryImpl
import com.m3u.data.repository.plugin.ExtensionPluginTransportConnector
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.repository.provider.SubscriptionProviderRepositoryImpl
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.runtime.CapabilityPolicy
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionBrokerScopeProvider
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionSettingsProvider
import com.m3u.extension.runtime.InvocationPolicy
import com.m3u.extension.transport.android.AndroidExtensionDiscovery
import com.m3u.extension.transport.android.ExtensionTrustStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ExtensionBindingsModule {
    @Binds
    @Singleton
    abstract fun bindExtensionContributionScheduler(
        scheduler: WorkManagerExtensionContributionScheduler,
    ): ExtensionContributionScheduler

    @Binds
    @Singleton
    abstract fun bindExtensionSettingsProvider(
        store: ExtensionSettingStore,
    ): ExtensionSettingsProvider

    @Binds
    @Singleton
    abstract fun bindExtensionSettingsRepository(
        repository: ExtensionSettingsRepositoryImpl,
    ): ExtensionSettingsRepository

    @Binds
    @Singleton
    abstract fun bindExtensionContributionRepository(
        repository: ExtensionContributionRepositoryImpl,
    ): ExtensionContributionRepository

    @Binds
    @Singleton
    abstract fun bindExtensionPluginRepository(
        repository: ExtensionPluginRepositoryImpl,
    ): ExtensionPluginRepository

    @Binds
    @Singleton
    abstract fun bindExtensionPluginDiscovery(
        discovery: AndroidExtensionPluginDiscovery,
    ): ExtensionPluginDiscovery

    @Binds
    @Singleton
    abstract fun bindExtensionPluginTransportConnector(
        connector: AndroidExtensionPluginTransportConnector,
    ): ExtensionPluginTransportConnector

    @Binds
    @Singleton
    abstract fun bindCredentialVault(
        vault: AndroidKeystoreCredentialVault,
    ): CredentialVault

    @Binds
    @Singleton
    abstract fun bindExtensionSecretStore(
        vault: AndroidKeystoreCredentialVault,
    ): ExtensionSecretStore

    @Binds
    @Singleton
    abstract fun bindHostNetworkBroker(
        broker: HostNetworkBrokerImpl,
    ): ProviderHostNetworkBroker

    @Binds
    @Singleton
    abstract fun bindExtensionBrokerScopeProvider(
        provider: ExtensionHookBrokerScopeProvider,
    ): ExtensionBrokerScopeProvider

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
    fun provideAndroidExtensionDiscovery(
        @ApplicationContext context: Context,
    ) = AndroidExtensionDiscovery(context)

    @Provides
    @Singleton
    fun provideExtensionTrustStore(
        @ApplicationContext context: Context,
    ) = ExtensionTrustStore(context)

    @Provides
    @Singleton
    fun provideExtensionInvocationPolicy(): InvocationPolicy = InvocationPolicy()

    @Provides
    @Singleton
    fun provideExtensionRuntime(
        provider: EmbyCompatibleProvider,
        trustStore: ExtensionTrustStore,
        settingsProvider: ExtensionSettingsProvider,
        brokerScopeProvider: ExtensionBrokerScopeProvider,
        invocationPolicy: InvocationPolicy,
    ): ExtensionRuntime = ExtensionRuntime(
        hostApiVersion = ExtensionApiVersions.Current,
        capabilityPolicy = CapabilityPolicy { manifest, _ ->
            val grantedIds = if (manifest.id == EmbyCompatibleProvider.ID) {
                manifest.capabilities.mapTo(mutableSetOf()) { it.capability.id }
            } else {
                trustStore.grantedCapabilities(manifest.id.value)
            }
            manifest.capabilities.mapNotNullTo(mutableSetOf()) { request ->
                request.capability.takeIf { it.id in grantedIds }
            }
        },
        settingsProvider = settingsProvider,
        brokerScopeProvider = brokerScopeProvider,
        invocationPolicy = invocationPolicy,
    ).apply {
        val registration = register(provider)
        check(registration is ExtensionRegistrationResult.Registered) {
            "Failed to register built-in Emby-compatible provider"
        }
    }
}
