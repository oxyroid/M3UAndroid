package com.m3u.androidApp.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.androidApp.ui.sheet.RemoteControlSheetValue
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.api.LocalPreparedService
import com.m3u.data.repository.ConnectionToTelevisionValue
import com.m3u.data.repository.TelevisionRepository
import com.m3u.data.repository.UpdateKey
import com.m3u.data.repository.UpdateState
import com.m3u.data.service.Messager
import com.m3u.data.television.model.RemoteDirection
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    messager: Messager,
    private val televisionRepository: TelevisionRepository,
    private val localService: LocalPreparedService,
    private val pref: Pref,
    private val publisher: Publisher,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
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

    internal val remoteControlSheetValue: StateFlow<RemoteControlSheetValue> = combine(
        televisionRepository.connected,
        snapshotFlow { code },
        connectionToTelevisionValue,
        televisionRepository.allUpdateStates
    ) { television, code, connection, states ->
        when {
            television == null -> {
                RemoteControlSheetValue.Prepare(
                    code = code,
                    searchingOrConnecting = with(connection) {
                        this is ConnectionToTelevisionValue.Searching ||
                                this is ConnectionToTelevisionValue.Connecting
                    }
                )
            }

            television.version != publisher.versionCode -> {
                this.code = ""
                val query = UpdateKey(television.version, television.abi)
                val state = states[query] ?: UpdateState.Idle
                RemoteControlSheetValue.Update(
                    television = television,
                    state = state
                )
            }

            else -> {
                this.code = ""
                RemoteControlSheetValue.DPad(
                    television = television,
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = RemoteControlSheetValue.Idle,
            started = SharingStarted.Lazily
        )

    private var checkTelevisionCodeOnSmartphoneJob: Job? = null

    internal fun checkTelevisionCodeOnSmartphone() {
        viewModelScope.launch {
            televisionCodeOnSmartphone.emit(code)
        }
        checkTelevisionCodeOnSmartphoneJob?.cancel()
        checkTelevisionCodeOnSmartphoneJob = pref.observeAsFlow { it.remoteControl }
            .onEach { remoteControl ->
                if (!remoteControl) {
                    forgetTelevisionCodeOnSmartphone()
                }
            }
            .launchIn(viewModelScope)
    }

    internal fun forgetTelevisionCodeOnSmartphone() {
        viewModelScope.launch {
            televisionCodeOnSmartphone.emit("")
        }
    }

    internal fun onRemoteDirection(remoteDirection: RemoteDirection) {
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

    val title = mutableStateOf("")
    val actions: MutableState<ImmutableList<Action>> = mutableStateOf(persistentListOf())
    val fob = mutableStateOf<Fob?>(null)
    var code by mutableStateOf("")
    var isConnectSheetVisible by mutableStateOf(false)
    val message = messager.message
}

private fun convertToPaddedString(code: Int, length: Int = 6): String {
    val codeString = code.toString()
    check(codeString.length <= length) { "Code($code) length is out of limitation($length)." }
    return codeString.let {
        "0".repeat(length - it.length) + it
    }
}