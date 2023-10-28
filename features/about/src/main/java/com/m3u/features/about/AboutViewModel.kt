package com.m3u.features.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.execute
import com.m3u.core.util.collections.indexOf
import com.m3u.data.api.GithubApi
import com.m3u.features.about.model.Contributor
import com.m3u.features.about.model.toContributor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    application: Application,
    private val api: GithubApi,
    private val client: OkHttpClient,
    @Publisher.App private val publisher: Publisher,
    private val logger: Logger
) : AndroidViewModel(application) {
    private val _contributors: MutableStateFlow<ImmutableList<Contributor>> = MutableStateFlow(persistentListOf())
    internal val contributors: StateFlow<ImmutableList<Contributor>> = _contributors.asStateFlow()

    private val _dependencies: MutableStateFlow<ImmutableList<String>> = MutableStateFlow(persistentListOf())
    internal val dependencies: StateFlow<ImmutableList<String>> = _dependencies.asStateFlow()

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
                _contributors.value = users
                    .map { it.toContributor() }
                    .sortedByDescending { it.contributions }
                    .toPersistentList()
            }
            val catalogs = fetchVersionCatalogs()
            _dependencies.value = catalogs.readTomlDependencies().toPersistentList()
        }
    }

    private suspend fun fetchVersionCatalogs(): List<String> = withContext(Dispatchers.IO) {
        logger.execute {
            val response = client
                .newCall(
                    Request.Builder()
                        .url("https://raw.githubusercontent.com/realOxy/M3UAndroid/master/gradle/libs.versions.toml")
                        .build()
                )
                .execute()
            if (!response.isSuccessful) return@execute emptyList()
            val content = response.body?.bytes()?.decodeToString().orEmpty()
            content.lines()
        } ?: emptyList()
    }

    private fun List<String>.readTomlDependencies(): List<String> = logger.execute {
        val start = indexOf { it.startsWith(("[libraries]")) } + 1
        val end = indexOf(start) { it.startsWith(("[plugins]")) }
        subList(start, end).mapNotNull { line ->
            val i = line.indexOf("=")
            if (i == -1) null
            else line.take(i).trim()
        }
            .sorted()
    } ?: emptyList()
}
