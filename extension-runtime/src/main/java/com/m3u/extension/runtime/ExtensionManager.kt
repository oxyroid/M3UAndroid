package com.m3u.extension.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class ExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ExtensionReceiver.Listener {
    private val receiver = ExtensionReceiver(this)
    private val coroutineScope = CoroutineScope(SupervisorJob())
    private val _packages = MutableStateFlow<List<ApplicationInfo>>(emptyList())
    val packages: StateFlow<List<ApplicationInfo>> = _packages.asStateFlow()
    val extensions: StateFlow<List<Extension>> = packages
        .map { infos ->
            infos.mapNotNull {
                ExtensionLoader.loadExtension(context, it)
            }
        }
        .stateIn(
            scope = coroutineScope,
            initialValue = emptyList(),
            started = SharingStarted.Lazily
        )


    init {
        receiver.register(context)
    }

    private var refreshJob: Job? = null

    fun refreshExtensionPackages() {
        refreshJob?.cancel()
        refreshJob = coroutineScope.launch {
            _packages.value = getExtensionPackages()
        }
    }

    // TODO
    override fun onExtensionInstalled(extension: Extension) {
        refreshExtensionPackages()
    }

    override fun onExtensionUpdated(extension: Extension) {
        refreshExtensionPackages()
    }

    override fun onPackageUninstalled(pkgName: String) {
        refreshExtensionPackages()
    }

    private suspend fun getExtensionPackages(): List<ApplicationInfo> = ExtensionLoader
        .loadExtensionPackages(context)
        .toList()
}