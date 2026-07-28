package com.m3u.business.setting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface ExtensionPluginOperation {
    data object Refresh : ExtensionPluginOperation

    data class Enable(
        val packageName: String,
        val serviceName: String,
    ) : ExtensionPluginOperation

    data class Reauthorize(
        val packageName: String,
        val serviceName: String,
    ) : ExtensionPluginOperation

    data class Disable(
        val extensionId: String,
    ) : ExtensionPluginOperation

    data class ClearData(
        val packageName: String,
        val serviceName: String,
        val extensionId: String,
    ) : ExtensionPluginOperation

    data class Revoke(
        val packageName: String,
        val serviceName: String,
        val extensionId: String,
    ) : ExtensionPluginOperation
}

sealed interface ExtensionPluginOperationState {
    data object Idle : ExtensionPluginOperationState

    data class Running(
        val operation: ExtensionPluginOperation,
    ) : ExtensionPluginOperationState
}

internal class ExtensionPluginOperationController {
    private val lock = Any()
    private var refreshPending = false
    private val _state = MutableStateFlow<ExtensionPluginOperationState>(
        ExtensionPluginOperationState.Idle
    )
    val state: StateFlow<ExtensionPluginOperationState> = _state

    fun tryStart(
        operation: ExtensionPluginOperation,
        queueRefreshIfBusy: Boolean = false,
    ): ExtensionPluginOperationState.Running? {
        val running = ExtensionPluginOperationState.Running(operation)
        return synchronized(lock) {
            if (_state.value != ExtensionPluginOperationState.Idle) {
                if (
                    queueRefreshIfBusy &&
                    operation == ExtensionPluginOperation.Refresh
                ) {
                    refreshPending = true
                }
                null
            } else {
                _state.value = running
                running
            }
        }
    }

    fun finishAndConsumePendingRefresh(
        running: ExtensionPluginOperationState.Running,
    ): Boolean =
        synchronized(lock) {
            if (_state.value === running) {
                _state.value = ExtensionPluginOperationState.Idle
                refreshPending.also { refreshPending = false }
            } else {
                false
            }
        }
}
