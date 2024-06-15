package com.m3u.androidApp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.m3u.androidApp.ui.sheet.RemoteControlSheetValue
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.data.api.LeanbackApiDelegate
import com.m3u.data.leanback.model.RemoteDirection
import com.m3u.data.repository.leanback.ConnectionToLeanbackValue
import com.m3u.data.repository.leanback.LeanbackRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.service.Messager
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val playlistRepository: PlaylistRepository,
    private val programmeRepository: ProgrammeRepository,
    private val leanbackRepository: LeanbackRepository,
    private val leanbackApi: LeanbackApiDelegate,
    private val workManager: WorkManager,
    private val preferences: Preferences,
    private val publisher: Publisher,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    init {
        refreshProgrammes()
    }

    val broadcastCodeOnLeanback: StateFlow<String?> = leanbackRepository
        .broadcastCodeOnLeanback
        .map { code -> code?.let { convertToPaddedString(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val leanbackCodeOnSmartphone = MutableSharedFlow<String>()

    private val connectionToLeanbackValue: StateFlow<ConnectionToLeanbackValue> =
        leanbackCodeOnSmartphone.flatMapLatest { code ->
            if (code.isNotEmpty()) leanbackRepository.connectToLeanback(code.toInt())
            else {
                leanbackRepository.disconnectToLeanback()
                flowOf(ConnectionToLeanbackValue.Idle())
            }
        }
            .flowOn(ioDispatcher)
            .stateIn(
                scope = viewModelScope,
                initialValue = ConnectionToLeanbackValue.Idle(),
                started = SharingStarted.WhileSubscribed(5_000)
            )

    internal val remoteControlSheetValue: StateFlow<RemoteControlSheetValue> = combine(
        leanbackRepository.connected,
        snapshotFlow { code },
        connectionToLeanbackValue
    ) { leanback, code, connection ->
        when {
            leanback == null -> {
                RemoteControlSheetValue.Prepare(
                    code = code,
                    searchingOrConnecting = with(connection) {
                        this is ConnectionToLeanbackValue.Searching ||
                                this is ConnectionToLeanbackValue.Connecting
                    }
                )
            }

            leanback.version != publisher.versionCode -> {
                this.code = ""
//                val query = UpdateKey(leanback.version, leanback.abi)
//                val state = states[query] ?: UpdateState.Idle
//                RemoteControlSheetValue.Update(
//                    leanback = leanback,
//                    state = state
//                )
                RemoteControlSheetValue.Prepare(
                    code = code,
                    searchingOrConnecting = with(connection) {
                        this is ConnectionToLeanbackValue.Searching ||
                                this is ConnectionToLeanbackValue.Connecting
                    }
                )
            }

            else -> {
                this.code = ""
                RemoteControlSheetValue.DPad(
                    leanback = leanback,
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = RemoteControlSheetValue.Idle,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    private var checkLeanbackCodeOnSmartphoneJob: Job? = null

    internal fun checkLeanbackCodeOnSmartphone() {
        viewModelScope.launch {
            leanbackCodeOnSmartphone.emit(code)
        }
        checkLeanbackCodeOnSmartphoneJob?.cancel()
        checkLeanbackCodeOnSmartphoneJob = snapshotFlow { preferences.remoteControl }
            .onEach { remoteControl ->
                if (!remoteControl) {
                    forgetLeanbackCodeOnSmartphone()
                }
            }
            .launchIn(viewModelScope)
    }

    internal fun forgetLeanbackCodeOnSmartphone() {
        viewModelScope.launch {
            leanbackCodeOnSmartphone.emit("")
        }
    }

    internal fun onRemoteDirection(remoteDirection: RemoteDirection) {
        viewModelScope.launch {
            leanbackApi.remoteDirection(remoteDirection.value)
        }
    }

    private fun refreshProgrammes() {
        viewModelScope.launch {
            val playlists = playlistRepository.getAllAutoRefresh()
            playlists.forEach { playlist ->
                SubscriptionWorker.epg(
                    workManager = workManager,
                    playlistUrl = playlist.url,
                    ignoreCache = true
                )
            }
        }
    }

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