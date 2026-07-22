package com.m3u.data.repository.extension

import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.runtime.ExtensionRuntime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

internal class ExtensionContributionRepositoryImpl @Inject constructor(
    private val runtime: ExtensionRuntime,
) : ExtensionContributionRepository {
    override suspend fun search(query: String, limit: Int): List<ExtensionSearchContribution> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty() || limit <= 0) return emptyList()
        return supervisorScope {
            runtime.extensionsSupporting(HostHookSpecs.SearchProvider.hook)
                .filter { extension -> extension.state == ExtensionState.ENABLED }
                .map { extension ->
                    async {
                        try {
                            when (
                                val outcome = runtime.invoke(
                                    extension.manifest.id,
                                    HostHookSpecs.SearchProvider,
                                    SearchProviderRequest(normalizedQuery, limit),
                                ).outcome
                            ) {
                                is HookResult.Success -> outcome.payload.items.map { item ->
                                    ExtensionSearchContribution(extension.manifest.id, item)
                                }
                                is HookResult.Failure -> emptyList()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .distinctBy { contribution ->
                    contribution.extensionId.value to contribution.item.stableReference
                }
                .take(limit)
        }
    }
}
