package com.m3u.data.repository.extension

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.SearchProviderItem

interface ExtensionContributionRepository {
    suspend fun search(query: String, limit: Int = 50): List<ExtensionSearchContribution>
}

data class ExtensionSearchContribution(
    val extensionId: ExtensionId,
    val item: SearchProviderItem,
)
