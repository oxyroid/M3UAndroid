package com.m3u.data.extension.security

import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.repository.extension.ExtensionSettingStore
import com.m3u.extension.api.EpgRefreshRequest
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.MetadataEnrichmentRequest
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.runtime.ExtensionBrokerScopeLease
import com.m3u.extension.runtime.ExtensionBrokerScopeProvider
import com.m3u.extension.runtime.ExtensionBrokerScopeRequest
import com.m3u.extension.transport.android.ExtensionTrustStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ExtensionHookBrokerScopeProvider @Inject constructor(
    private val principalRegistry: ActiveExtensionPrincipalRegistry,
    private val providerDao: ProviderDao,
    private val scopeStore: ProviderBrokerScopeStore,
    private val settingStore: ExtensionSettingStore,
    private val trustStore: ExtensionTrustStore,
) : ExtensionBrokerScopeProvider {
    override suspend fun open(
        request: ExtensionBrokerScopeRequest,
    ): ExtensionBrokerScopeLease? {
        if (request.hook !in AUTOMATIC_BROKER_HOOKS) return null
        val declaration = request.manifest.hooks.singleOrNull { candidate ->
            candidate.hook == request.hook
        } ?: return null
        if (
            ExtensionCapabilityIds.Network !in declaration.requiredCapabilities ||
            ExtensionCapabilityIds.Network !in request.grantedCapabilities
        ) {
            return null
        }
        val principal = principalRegistry.active(request.manifest.id) ?: return null
        val credentialReadAllowed =
            ExtensionCapabilityIds.CredentialRead in declaration.requiredCapabilities &&
                ExtensionCapabilityIds.CredentialRead in request.grantedCapabilities
        val extensionCredentials = if (credentialReadAllowed) {
            settingStore.resolveBrokerCredentials(
                extensionId = request.manifest.id.value,
                handles = request.settings.credentialHandles.values,
            )
        } else {
            emptyMap()
        }
        val accountBinding = request.payload.accountBinding()
        val scope = if (accountBinding != null) {
            mintAccountScope(
                principal = principal,
                request = request,
                binding = accountBinding,
                extensionCredentials = extensionCredentials,
                credentialReadAllowed = credentialReadAllowed,
            )
        } else {
            val currentManifestOrigins = request.manifest.networkOrigins
                .mapTo(mutableSetOf()) { origin -> origin.canonicalValue }
            val approvedManifestOrigins = trustStore.approvedNetworkOrigins(
                request.manifest.id.value
            )
            val approvedOrigins = currentManifestOrigins
                .intersect(approvedManifestOrigins) +
                settingStore.approvedSettingOrigins(
                    extensionId = request.manifest.id.value,
                    snapshot = request.settings,
                )
            if (approvedOrigins.isEmpty()) return null
            scopeStore.mintHookScope(
                principal = principal,
                allowedHook = request.hook,
                approvedOrigins = approvedOrigins,
                credentials = extensionCredentials,
            )
        }
        return ScopeLease(scopeStore, scope)
    }

    private suspend fun mintAccountScope(
        principal: ExtensionPrincipal,
        request: ExtensionBrokerScopeRequest,
        binding: AccountBinding,
        extensionCredentials: Map<CredentialHandle, String>,
        credentialReadAllowed: Boolean,
    ): BrokerScopeHandle {
        val account = providerDao.getAccount(binding.account.accountId)
            ?: throw SecurityException("Provider account is unavailable")
        require(account.matches(binding.account)) {
            "Provider account request does not match host state"
        }
        require(principal.owns(account)) {
            "Extension principal does not own the provider account"
        }
        require(!account.requiresReauthentication) {
            "Provider account requires reauthentication"
        }
        val credential = providerDao.getCredential(account.id)
            ?: throw SecurityException("Provider credential is unavailable")
        require(credential.credentialHandle == binding.credential.handle.value) {
            "Provider credential request does not match host state"
        }
        return if (credentialReadAllowed) {
            scopeStore.mintAccountScope(
                principal = principal,
                allowedHook = request.hook,
                account = account,
                credential = credential,
                additionalCredentials = extensionCredentials,
            )
        } else {
            scopeStore.mintAccountNetworkScope(
                principal = principal,
                allowedHook = request.hook,
                account = account,
            )
        }
    }

    private fun ExtensionPayload.accountBinding(): AccountBinding? = when (this) {
        is SearchProviderRequest -> account?.let { account ->
            AccountBinding(account, checkNotNull(credential))
        }
        is MetadataEnrichmentRequest -> account?.let { account ->
            AccountBinding(account, checkNotNull(credential))
        }
        is EpgRefreshRequest -> account?.let { account ->
            AccountBinding(account, checkNotNull(credential))
        }
        else -> null
    }

    private fun ProviderAccount.matches(reference: ProviderAccountReference): Boolean =
        id == reference.accountId &&
            providerId == reference.providerId.value &&
            providerKind == reference.providerKind.value &&
            baseUrl == reference.baseUrl &&
            serverId == reference.serverId &&
            serverName == reference.serverName &&
            serverVersion == reference.serverVersion &&
            userId == reference.userId &&
            username == reference.username

    private data class AccountBinding(
        val account: ProviderAccountReference,
        val credential: ProviderCredential,
    )

    private class ScopeLease(
        private val store: ProviderBrokerScopeStore,
        override val handle: BrokerScopeHandle,
    ) : ExtensionBrokerScopeLease {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                store.close(handle)
            }
        }
    }

    private companion object {
        val AUTOMATIC_BROKER_HOOKS = setOf(
            ExtensionHookIds.MetadataChannelEnrich,
            ExtensionHookIds.EpgContentRefresh,
            ExtensionHookIds.SettingsSchemaContribute,
            ExtensionHookIds.SearchProviderQuery,
            ExtensionHookIds.BackgroundTaskRun,
        )
    }
}
