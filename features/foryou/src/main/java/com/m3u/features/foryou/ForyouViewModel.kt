package com.m3u.features.foryou

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Default
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.api.LocalPreparedService
import com.m3u.data.repository.ConnectionToTelevisionValue
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.TelevisionRepository
import com.m3u.data.television.model.RemoteDirection
import com.m3u.features.foryou.components.ConnectBottomSheetValue
import com.m3u.features.foryou.components.recommend.Recommend
import com.m3u.features.foryou.model.PlaylistDetail
import com.m3u.features.foryou.model.toDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@HiltViewModel
class ForyouViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    streamRepository: StreamRepository,
    private val televisionRepository: TelevisionRepository,
    private val localService: LocalPreparedService,
    pref: Pref,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(Default) defaultDispatcher: CoroutineDispatcher
) : ViewModel() {
    internal var code by mutableStateOf("")
    internal var isConnectSheetVisible by mutableStateOf(false)

    internal val broadcastCodeOnTelevision: StateFlow<String?> = televisionRepository
        .broadcastCodeOnTelevision
        .map { code -> code?.let { convertToPaddedString(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val counts: StateFlow<Map<String, Int>> = streamRepository
        .observeAll()
        .map { streams ->
            // map playlistUrl to count
            streams
                .groupingBy { it.playlistUrl }
                .eachCount()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    internal val details: StateFlow<ImmutableList<PlaylistDetail>> = playlistRepository
        .observeAll()
        .distinctUntilChanged()
        .combine(counts) { playlists, counts ->
            withContext(defaultDispatcher) {
                playlists.map { playlist ->
                    val count = counts[playlist.url] ?: PlaylistDetail.DEFAULT_COUNT
                    playlist.toDetail(count)
                }
            }
                .toPersistentList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = persistentListOf()
        )

    private val unseensDuration = pref
        .observeAsFlow { it.unseensMilliseconds }
        .map { it.toDuration(DurationUnit.MILLISECONDS) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = Duration.INFINITE
        )

    internal val recommend: StateFlow<Recommend> = unseensDuration
        .flatMapLatest { streamRepository.observeAllUnseenFavourites(it) }
        .map { prev -> Recommend(prev.map { Recommend.UnseenSpec(it) }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = Recommend()
        )

    internal fun unsubscribe(url: String) {
        viewModelScope.launch {
            playlistRepository.unsubscribe(url)
        }
    }

    internal fun rename(playlistUrl: String, target: String) {
        viewModelScope.launch {
            playlistRepository.rename(playlistUrl, target)
        }
    }

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
}

private fun convertToPaddedString(code: Int, length: Int = 6): String {
    val codeString = code.toString()
    check(codeString.length <= length) { "Code($code) length is out of limitation($length)." }
    return codeString.let {
        "0".repeat(length - it.length) + it
    }
}