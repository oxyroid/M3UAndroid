package com.m3u.features.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.Logger
import com.m3u.data.service.PlayerManager
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mm2d.upnp.ControlPoint
import net.mm2d.upnp.ControlPointFactory
import net.mm2d.upnp.Device
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    private val feedRepository: FeedRepository,
    application: Application,
    configuration: Configuration,
    private val playerManager: PlayerManager,
    private val logger: Logger
) : BaseViewModel<LiveState, LiveEvent>(
    application = application,
    emptyState = LiveState(
        configuration = configuration
    )
) {
    init {
        playerManager
            .observe()
            .onEach { player ->
                writable.update {
                    it.copy(
                        player = player,
                        muted = player?.volume == 0f
                    )
                }
            }
            .launchIn(viewModelScope)

        playerManager.initialize()

        combine(
            playerManager.playbackState,
            playerManager.videoSize,
            playerManager.playerError
        ) { playback, videoSize, playerError ->
            LiveState.PlayerState(
                playback = playback,
                videoSize = videoSize,
                playerError = playerError
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LiveState.PlayerState()
            )
            .onEach { playerState ->
                writable.value = readable.copy(
                    playerState = playerState
                )
            }
            .launchIn(viewModelScope)
    }

    private val controlPoint = ControlPointFactory.create().apply {
        initialize()
    }

    override fun onEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.InitSpecial -> initSpecial(event.liveId)
            is LiveEvent.InitPlayList -> initPlaylist(event.ids, event.initialIndex)
            LiveEvent.SearchDlnaDevices -> searchDlnaDevices()
            LiveEvent.StopSearchDlnaDevices -> stopSearchDlnaDevices()
            LiveEvent.ClearDlnaDevices -> clearDlnaDevices()
            is LiveEvent.ConnectDlnaDevice -> connectDlnaDevice(event.device)
            is LiveEvent.DisconnectDlnaDevice -> disconnectDlnaDevice(event.device)
            LiveEvent.Record -> record()
            is LiveEvent.OnFavourite -> setFavourite(event.url)
            is LiveEvent.InstallMedia -> {
                val url = event.url
                if (url.isEmpty()) return
                playerManager.install(url)
            }

            LiveEvent.UninstallMedia -> {
                playerManager.uninstall()
            }

            LiveEvent.OnMuted -> muted()
        }
    }

    private var initJob: Job? = null
    private fun initSpecial(id: Int) {
        initJob?.cancel()
        initJob = viewModelScope.launch {
            liveRepository.observe(id)
                .onEach { live ->
                    if (live != null) {
                        val feed = feedRepository.get(live.feedUrl)
                        writable.update {
                            it.copy(
                                init = LiveState.InitSingle(
                                    live = live,
                                    feed = feed
                                )
                            )
                        }
                    }
                }
                .launchIn(this)
        }
    }

    private fun initPlaylist(ids: List<Int>, initialIndex: Int) {
        initJob?.cancel()
        initJob = viewModelScope.launch {
            val lives = when (val init = readable.init) {
                is LiveState.InitPlayList -> init.lives
                is LiveState.InitSingle -> init.live?.let(::listOf) ?: emptyList()
            }.toMutableList()
            ids.forEach { id ->
                val live = liveRepository.get(id)
                if (live != null) {
                    lives.add(live)
                }
            }
            writable.update { readable ->
                readable.copy(
                    init = LiveState.InitPlayList(
                        lives = lives,
                        initialIndex = initialIndex
                    ),
                )
            }
        }
    }

    private val _isDevicesVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isDevicesVisible = _isDevicesVisible.asStateFlow()

    private val _searching: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    val devices: StateFlow<List<Device>?> = callbackFlow {
        val listener = object : ControlPoint.DiscoveryListener {
            override fun onDiscover(device: Device) {
                trySendBlocking(controlPoint.deviceList)
            }

            override fun onLost(device: Device) {
                trySendBlocking(controlPoint.deviceList)
            }
        }
        controlPoint.addDiscoveryListener(listener)
        awaitClose {
            controlPoint.removeDiscoveryListener(listener)
            controlPoint.clearDeviceList()
        }
    }
        .combine(isDevicesVisible) { d, v -> d.takeIf { v } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private fun searchDlnaDevices() {
        viewModelScope.launch {
            delay(800.milliseconds)
            controlPoint.start()
            controlPoint.search()
            _searching.value = true
        }
        _isDevicesVisible.value = true
    }

    private fun stopSearchDlnaDevices() {
        controlPoint.stop()
        _searching.value = false
    }

    private fun clearDlnaDevices() {
        stopSearchDlnaDevices()
        _isDevicesVisible.value = false
        controlPoint.clearDeviceList()
    }

    private fun connectDlnaDevice(device: Device) {
        val deviceList = device.deviceList
        val deviceType = device.deviceType
        val baseUrl = device.baseUrl
        val friendlyName = device.friendlyName
        val description = device.description
        val ipAddress = device.ipAddress
        val location = device.location
        val modelUrl = device.modelUrl
        val scopeId = device.scopeId
        // Browse Search CreateObject DestroyObject UpdateObject
        // http://upnp.org/specs/av/UPnP-av-ContentDirectory-v4-Service.pdf
        val serviceList = device.serviceList
        val browse = device.findAction("MagicOn")
        browse?.invoke(
            argumentValues = mapOf(
                "ObjectID" to "0",
                "BrowseFlag" to "BrowseDirectChildren",
                "Filter" to "*",
                "StartingIndex" to "0",
                "RequestedCount" to "0",
                "SortCriteria" to ""
            ),
            onResult = {
                val result = it["Result"]
                logger.log(result.orEmpty())
            },
            onError = { cause ->
                logger.log(cause)
            }
        )
        writable.update {
            it.copy(
                connectedDevices = it.connectedDevices + device
            )
        }
    }

    private fun disconnectDlnaDevice(device: Device) {
        writable.update {
            it.copy(
                connectedDevices = it.connectedDevices - device
            )
        }
    }

    private fun record() {
        writable.update {
            it.copy(
                recording = !readable.recording
            )
        }
    }

    private fun setFavourite(url: String) {
        viewModelScope.launch {
            val live = liveRepository.getByUrl(url) ?: return@launch
            val id = live.id
            val target = !live.favourite
            liveRepository.setFavourite(id, target)
        }
    }

    private fun muted() {
        val target = !readable.muted
        val volume = if (target) 0f else 1f
        readable.player?.volume = volume
        writable.update {
            it.copy(
                muted = target
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        controlPoint.terminate()
    }
}