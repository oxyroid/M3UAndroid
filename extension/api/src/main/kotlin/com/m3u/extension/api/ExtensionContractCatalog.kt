package com.m3u.extension.api

import com.m3u.extension.api.subscription.SubscriptionHookSpecs

class ExtensionHookContract<
    Request : ExtensionPayload,
    Response : ExtensionPayload,
>(
    val spec: HookSpec<Request, Response>,
    requiredCapabilities: Set<Capability> = emptySet(),
) {
    val requiredCapabilities: Set<Capability> = requiredCapabilities.toSet()
}

class ExtensionContractSet(
    contracts: Collection<ExtensionHookContract<*, *>>,
    supportedCapabilities: Set<Capability>,
) {
    val contracts: List<ExtensionHookContract<*, *>> = contracts.toList()
    val supportedCapabilities: Set<Capability> = supportedCapabilities.toSet()

    private val contractsByHookAndSchema =
        this.contracts.associateBy { contract ->
            HookSchemaKey(
                hook = contract.spec.hook,
                schemaVersion = contract.spec.schemaVersion,
            )
        }

    init {
        require(contractsByHookAndSchema.size == this.contracts.size) {
            "Extension contract set must not declare a Hook schema more than once"
        }
        val unknownCapabilities = this.contracts
            .flatMapTo(mutableSetOf()) { contract -> contract.requiredCapabilities }
            .minus(this.supportedCapabilities)
        require(unknownCapabilities.isEmpty()) {
            "Hook contracts require unsupported capabilities: $unknownCapabilities"
        }
        val inconsistentHookCapabilities = this.contracts
            .groupBy { contract -> contract.spec.hook }
            .filterValues { hookContracts ->
                hookContracts
                    .map { contract -> contract.requiredCapabilities }
                    .distinct()
                    .size > 1
            }
            .keys
        require(inconsistentHookCapabilities.isEmpty()) {
            "Schemas of one Hook must require the same base capabilities: " +
                inconsistentHookCapabilities
        }
    }

    val supportedHookSchemaVersions: Map<Hook, Set<Int>> = this.contracts
        .groupBy { contract -> contract.spec.hook }
        .mapValues { (_, hookContracts) ->
            hookContracts.mapTo(mutableSetOf()) { contract ->
                contract.spec.schemaVersion
            }
        }

    val requiredCapabilitiesByHook: Map<Hook, Set<Capability>> = this.contracts
        .groupBy { contract -> contract.spec.hook }
        .mapValues { (_, hookContracts) ->
            hookContracts.first().requiredCapabilities
        }
        .filterValues(Set<Capability>::isNotEmpty)

    fun contract(
        hook: Hook,
        schemaVersion: Int,
    ): ExtensionHookContract<*, *>? =
        contractsByHookAndSchema[HookSchemaKey(hook, schemaVersion)]

    fun containsCanonical(spec: HookSpec<*, *>): Boolean =
        contract(spec.hook, spec.schemaVersion)?.spec === spec

    private data class HookSchemaKey(
        val hook: Hook,
        val schemaVersion: Int,
    )
}

object ExtensionContractCatalog {
    val ContractSet = ExtensionContractSet(
        contracts = listOf(
            ExtensionHookContract(SubscriptionHookSpecs.Discover),
            ExtensionHookContract(
                SubscriptionHookSpecs.Validate,
                setOf(ExtensionCapabilityIds.CredentialWrite),
            ),
            ExtensionHookContract(
                SubscriptionHookSpecs.Refresh,
                setOf(ExtensionCapabilityIds.SubscriptionRead),
            ),
            ExtensionHookContract(
                SubscriptionHookSpecs.ResolvePlayback,
                setOf(ExtensionCapabilityIds.PlaybackResolve),
            ),
            ExtensionHookContract(
                SubscriptionHookSpecs.ClosePlayback,
                setOf(ExtensionCapabilityIds.PlaybackResolve),
            ),
            ExtensionHookContract(
                HostHookSpecs.MetadataEnrichment,
                setOf(ExtensionCapabilityIds.MetadataWrite),
            ),
            ExtensionHookContract(
                HostHookSpecs.EpgRefresh,
                setOf(ExtensionCapabilityIds.EpgRead),
            ),
            ExtensionHookContract(
                HostHookSpecs.SettingsSchema,
                setOf(ExtensionCapabilityIds.SettingsContribute),
            ),
            ExtensionHookContract(
                HostHookSpecs.SearchProvider,
                setOf(ExtensionCapabilityIds.SearchRead),
            ),
            ExtensionHookContract(
                HostHookSpecs.BackgroundTask,
                setOf(ExtensionCapabilityIds.BackgroundTask),
            ),
        ),
        supportedCapabilities = ExtensionCapabilityIds.All,
    )

    val SupportedHookSpecs: List<HookSpec<*, *>> =
        ContractSet.contracts.map { contract -> contract.spec }

    val SupportedHookSchemaVersions: Map<Hook, Set<Int>> =
        ContractSet.supportedHookSchemaVersions

    val SupportedCapabilities: Set<Capability> =
        ContractSet.supportedCapabilities

    val RequiredCapabilitiesByHook: Map<Hook, Set<Capability>> =
        ContractSet.requiredCapabilitiesByHook

    fun contract(
        hook: Hook,
        schemaVersion: Int,
    ): ExtensionHookContract<*, *>? = ContractSet.contract(hook, schemaVersion)

    fun containsCanonical(spec: HookSpec<*, *>): Boolean =
        ContractSet.containsCanonical(spec)
}
