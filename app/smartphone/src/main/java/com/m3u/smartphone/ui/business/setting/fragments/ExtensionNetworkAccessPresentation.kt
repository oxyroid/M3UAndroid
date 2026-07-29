package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.data.repository.extension.ExtensionNetworkOriginState
import com.m3u.data.repository.extension.ExtensionSettingNetworkOrigin
import com.m3u.data.repository.plugin.InstalledPlugin

internal data class ExtensionNetworkAccessCounts(
    val approved: Int,
    val total: Int,
)

internal fun InstalledPlugin.visibleSettingNetworkOrigins(): List<ExtensionSettingNetworkOrigin> =
    networkAccess.settingOrigins.filter { origin ->
        origin.state != ExtensionNetworkOriginState.NOT_CONFIGURED
    }

internal fun InstalledPlugin.networkAccessCounts(): ExtensionNetworkAccessCounts {
    val visibleSettingOrigins = visibleSettingNetworkOrigins()
    return ExtensionNetworkAccessCounts(
        approved = networkAccess.fixedOrigins.count { origin ->
            origin.state == ExtensionNetworkOriginState.APPROVED
        } + visibleSettingOrigins.count { origin ->
            origin.state == ExtensionNetworkOriginState.APPROVED
        },
        total = networkAccess.fixedOrigins.size + visibleSettingOrigins.size,
    )
}

internal val InstalledPlugin.hasSettingNetworkOriginWarning: Boolean
    get() = networkAccess.settingOrigins.any { origin ->
        origin.state.requiresUserAttention
    }

internal val ExtensionNetworkOriginState.requiresUserAttention: Boolean
    get() = when (this) {
        ExtensionNetworkOriginState.INVALID,
        ExtensionNetworkOriginState.REQUIRES_APPROVAL,
        ExtensionNetworkOriginState.SUSPENDED,
        ExtensionNetworkOriginState.UNVERIFIED,
        -> true

        ExtensionNetworkOriginState.NOT_CONFIGURED,
        ExtensionNetworkOriginState.APPROVED,
        -> false
    }
