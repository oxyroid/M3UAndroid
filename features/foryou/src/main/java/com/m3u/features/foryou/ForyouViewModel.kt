package com.m3u.features.foryou

import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.net.udp.LocalCode
import com.m3u.data.net.udp.LocalCodeBroadcast
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.features.foryou.components.recommend.Recommend
import com.m3u.features.foryou.model.PlaylistDetail
import com.m3u.features.foryou.model.toDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.net.NetworkInterface
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@HiltViewModel
class ForyouViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    streamRepository: StreamRepository,
    private val wifiManager: WifiManager,
    pref: Pref
) : ViewModel() {
    private val counts: StateFlow<Map<String, Int>> = streamRepository
        .observeAll()
        .map { streams ->
            streams
                .groupBy { it.playlistUrl }
                .mapValues { it.value.size }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    internal val details: StateFlow<ImmutableList<PlaylistDetail>> = playlistRepository
        .observeAll()
        .distinctUntilChanged()
        .combine(counts) { fs, cs ->
            withContext(Dispatchers.Default) {
                fs.map { f ->
                    f.toDetail(cs[f.url] ?: PlaylistDetail.DEFAULT_COUNT)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val recommend: StateFlow<Recommend> = unseensDuration
        .flatMapLatest { streamRepository.observeAllUnseenFavourites(it) }
        .map { prev -> Recommend(prev.map { Recommend.UnseenSpec(it) }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = Recommend()
        )

    fun unsubscribe(url: String) {
        viewModelScope.launch {
            playlistRepository.unsubscribe(url)
        }
    }

    fun rename(playlistUrl: String, target: String) {
        viewModelScope.launch {
            playlistRepository.rename(playlistUrl, target)
        }
    }

    private val _localCodes = MutableStateFlow<Set<LocalCode>>(emptySet())
    val localCodes = _localCodes.asStateFlow()

    private var searchLocalCodesJob: Job? = null
    fun searchLocalCodes() {
        stopSearchLocalCodes()
        searchLocalCodesJob = LocalCodeBroadcast
            .receive()
            .onEach { bytes ->
                _localCodes.value += try {
                    LocalCode.decode(bytes)
                } catch (ignored: Exception) {
                    return@onEach
                }
            }
            .launchIn(viewModelScope)
    }

    fun stopSearchLocalCodes() {
        searchLocalCodesJob?.cancel()
        _localCodes.value = emptySet()
    }

    private val _currentLocalCode = MutableStateFlow<LocalCode?>(null)
    val currentLocalCode = _currentLocalCode.asStateFlow()

    private var sendLocalCodeJob: Job? = null
    fun sendLocalCode() {
        stopSendLocalCode()
        val duration = 30.seconds
        viewModelScope.launch {
            while (true) {
                val now = Clock.System.now()
                val localCode = LocalCode(
                    host = getIpAddress() ?: return@launch,
                    port = Random.nextInt(49152, 65535),
                    code = Random.nextInt(999999),
                    expiration = (now + duration).toEpochMilliseconds()
                )
                _currentLocalCode.value = localCode
                val bytes = LocalCode.encode(localCode)
                LocalCodeBroadcast.send(bytes)
            }
        }
    }

    fun stopSendLocalCode() {
        searchLocalCodesJob?.cancel()
        _currentLocalCode.value = null
    }

    private fun getIpAddress(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val interfaze = interfaces.nextElement()
            val wlan = interfaze.name.contains("wlan", true) ||
                    interfaze.name.contains("ap", true)
            if (wlan) {
                val addresses = interfaze.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.address.size == 4
                    ) {
                        return address.hostAddress
                    }
                }
            }
        }
        return null
    }
}
