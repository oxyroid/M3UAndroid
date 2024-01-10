package com.m3u.features.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.data.api.GithubApi
import com.m3u.data.repository.parser.VersionCatalogParser
import com.m3u.features.about.model.Contributor
import com.m3u.features.about.model.toContributor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val api: GithubApi,
    private val parser: VersionCatalogParser,
    private val client: OkHttpClient,
    @Publisher.App private val publisher: Publisher,
    private val logger: Logger
) : ViewModel() {
    private val contributors: MutableStateFlow<List<Contributor>> =
        MutableStateFlow(emptyList())
    private val versionCatalog: MutableStateFlow<List<VersionCatalogParser.Entity>> =
        MutableStateFlow(emptyList())
    private val libraries = versionCatalog
        .map { entities ->
            val versions = entities.filterIsInstance<VersionCatalogParser.Entity.Version>()
            entities.mapNotNull { prev ->
                when (prev) {
                    is VersionCatalogParser.Entity.Library -> {
                        prev.copy(
                            ref = versions.find { it.key == prev.ref }?.value ?: prev.ref
                        )
                    }

                    else -> null
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    internal val s: StateFlow<AboutState> = combine(
        contributors,
        libraries
    ) { cs, ls -> AboutState(cs, ls) }
        .stateIn(
            scope = viewModelScope,
            initialValue = AboutState(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch parent@{
            launch {
                val users = logger.execute {
                    api.contributors(
                        publisher.author,
                        publisher.repository
                    )
                } ?: emptyList()
                contributors.value = users
                    .map { it.toContributor() }
                    .sortedByDescending { it.contributions }
            }
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/realOxy/M3UAndroid/master/gradle/libs.versions.toml")
                .build()
            val response = withContext(Dispatchers.IO) {
                logger.execute {
                    client
                        .newCall(request)
                        .execute()
                }
            }
            val input = response?.body?.byteStream()
            versionCatalog.update {
                input?.use { parser.execute(it) } ?: emptyList()
            }
        }
    }
}
