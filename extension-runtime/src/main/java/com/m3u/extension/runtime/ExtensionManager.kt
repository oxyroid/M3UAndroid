package com.m3u.extension.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import com.m3u.extension.api.workflow.Workflow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class ExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionLoader: ExtensionLoader,
) : ExtensionReceiver.Listener {
    private val receiver = ExtensionReceiver(extensionLoader, this)
    private val coroutineScope = CoroutineScope(SupervisorJob())
    private val _packages = MutableStateFlow<List<ApplicationInfo>>(emptyList())
    val packages: StateFlow<List<ApplicationInfo>> = _packages.asStateFlow()
    val extensions: StateFlow<List<Extension>> = packages
        .map { infos ->
            infos.mapNotNull { extensionLoader.loadExtension(context, it) }
        }
        .stateIn(
            scope = coroutineScope,
            initialValue = emptyList(),
            started = SharingStarted.Lazily
        )

    init {
        receiver.register(context)
        refreshExtensionPackages()
    }

    private var refreshJob: Job? = null

    private fun refreshExtensionPackages() {
        refreshJob?.cancel()
        refreshJob = coroutineScope.launch {
            val currentPackages = mutableListOf<ApplicationInfo>()
            extensionLoader.loadExtensionPackages(context)
                .onEach {
                    currentPackages += it
                    _packages.value = currentPackages
                }
                .flowOn(Dispatchers.IO)
                .launchIn(this)
        }
    }

    private val workflows = mutableMapOf<WorkflowKey, Workflow?>()

    private data class WorkflowKey(
        val pkgName: String,
        val classPath: String
    )

    suspend fun loadWorkflow(pkgName: String, classPath: String): Workflow? = workflows.getOrPut(
        WorkflowKey(pkgName, classPath)
    ) {
        extensionLoader.loadWorkflow(
            context = context,
            pkgName = pkgName,
            classPath = classPath
        )
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
}