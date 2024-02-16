package com.m3u.androidApp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.pref.Pref
import com.m3u.data.api.LocalPreparedService
import com.m3u.data.repository.ConnectionToTelevisionValue
import com.m3u.data.repository.TelevisionRepository
import com.m3u.data.service.MessageManager
import com.m3u.data.television.model.RemoteDirection
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    messageManager: MessageManager,
    private val televisionRepository: TelevisionRepository,
    private val localService: LocalPreparedService,
    pref: Pref,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val message = messageManager.message

    var code by mutableStateOf("")
    var isConnectSheetVisible by mutableStateOf(false)

    val broadcastCodeOnTelevision: StateFlow<String?> = televisionRepository
        .broadcastCodeOnTelevision
        .map { code -> code?.let { convertToPaddedString(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val televisionCodeOnSmartphone = MutableSharedFlow<String>()

    private val connectionToTelevisionValue: StateFlow<ConnectionToTelevisionValue> =
        televisionCodeOnSmartphone.flatMapLatest { code ->
            if (code.isNotEmpty()) televisionRepository.connectToTelevision(code.toInt())
            else {
                televisionRepository.disconnectToTelevision()
                flowOf(ConnectionToTelevisionValue.Idle())
            }
        }
            .flowOn(ioDispatcher)
            .stateIn(
                scope = viewModelScope,
                initialValue = ConnectionToTelevisionValue.Idle(),
                started = SharingStarted.WhileSubscribed(5_000)
            )

    internal val connectBottomSheetValue: StateFlow<ConnectBottomSheetValue> = combine(
        televisionRepository.connectedTelevision,
        snapshotFlow { code },
        connectionToTelevisionValue
    ) { television, code, connection ->
        when (television) {
            null -> ConnectBottomSheetValue.Prepare(
                code = code,
                searching = connection is ConnectionToTelevisionValue.Searching ||
                        connection is ConnectionToTelevisionValue.Connecting,
                onSearch = ::openTelevisionCodeOnSmartphone,
                onCode = { this.code = it }
            )

            else -> {
                this.code = ""
                ConnectBottomSheetValue.Remote(
                    television = television,
                    onRemoteDirection = ::onRemoteDirection,
                    onDisconnect = ::closeTelevisionCodeOnSmartphone
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = ConnectBottomSheetValue.Idle,
            started = SharingStarted.Lazily
        )

    private fun openTelevisionCodeOnSmartphone() {
        viewModelScope.launch {
            televisionCodeOnSmartphone.emit(code)
        }
    }

    private fun closeTelevisionCodeOnSmartphone() {
        viewModelScope.launch {
            televisionCodeOnSmartphone.emit("")
        }
    }

    private fun onRemoteDirection(remoteDirection: RemoteDirection) {
        viewModelScope.launch {
            localService.remoteDirection(remoteDirection.value)
        }
    }

    var rootDestination: Destination.Root by mutableStateOf(
        when (pref.rootDestination) {
            0 -> Destination.Root.Foryou
            1 -> Destination.Root.Favourite
            2 -> Destination.Root.Setting
            else -> Destination.Root.Foryou
        }
    )
    val title: MutableStateFlow<String> = MutableStateFlow("")
    val actions: MutableStateFlow<ImmutableList<Action>> = MutableStateFlow(persistentListOf())
    val fob: MutableStateFlow<Fob?> = MutableStateFlow(null)
    val deep: MutableStateFlow<Int> = MutableStateFlow(0)
}

private fun convertToPaddedString(code: Int, length: Int = 6): String {
    val codeString = code.toString()
    check(codeString.length <= length) { "Code($code) length is out of limitation($length)." }
    return codeString.let {
        "0".repeat(length - it.length) + it
    }
}