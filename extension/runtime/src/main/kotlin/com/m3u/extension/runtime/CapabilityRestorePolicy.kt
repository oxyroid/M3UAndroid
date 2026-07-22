package com.m3u.extension.runtime

import com.m3u.extension.api.ExtensionContractCatalog
import com.m3u.extension.api.ExtensionManifest

data class CapabilityRestoreDecision(
    val granted: Set<String>,
    val missingRequired: Set<String>,
) {
    val requiresReauthorization: Boolean
        get() = missingRequired.isNotEmpty()
}

fun reconcileCapabilitiesForRestore(
    manifest: ExtensionManifest,
    previousGrants: Set<String>,
): CapabilityRestoreDecision {
    val supportedRequests = manifest.capabilities.filter { request ->
        request.capability in ExtensionContractCatalog.SupportedCapabilities
    }
    val requested = supportedRequests.mapTo(mutableSetOf()) { request -> request.capability.id }
    val missingRequired = supportedRequests
        .asSequence()
        .filter { request -> request.required }
        .map { request -> request.capability.id }
        .filterNot(previousGrants::contains)
        .toSet()
    return CapabilityRestoreDecision(
        granted = previousGrants.intersect(requested),
        missingRequired = missingRequired,
    )
}
