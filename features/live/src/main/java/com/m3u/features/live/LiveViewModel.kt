package com.m3u.features.live

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.EmptyMessage
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.service.PlayerManager
import com.m3u.dlna.DLNACastManager
import com.m3u.dlna.OnDeviceRegistryListener
import com.m3u.dlna.control.DeviceControl
import com.m3u.dlna.control.OnDeviceControlListener
import com.m3u.dlna.control.ServiceActionCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fourthline.cling.model.meta.Device
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    private val feedRepository: FeedRepository,
    private val playerManager: PlayerManager,
    private val logger: Logger,
    private val application: Application
) : BaseViewModel<LiveState, LiveEvent, EmptyMessage>(
    emptyState = LiveState()
), OnDeviceRegistryListener, OnDeviceControlListener {
    private val _devices = MutableStateFlow<List<Device<*, *, *>>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _volume: MutableStateFlow<Float> = MutableStateFlow(1f)
    val volume = _volume.asStateFlow()

    val metadata: StateFlow<LiveState.Metadata> = playerManager
        .url
        .map { url ->
            val live = url?.let { liveRepository.getByUrl(it) }
            val feed = live?.feedUrl?.let { feedRepository.get(it) }
            LiveState.Metadata(feed, live)
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = LiveState.Metadata(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val playerState: StateFlow<LiveState.PlayerState> = combine(
        playerManager.observe(),
        playerManager.playbackState,
        playerManager.videoSize,
        playerManager.playerError
    ) { player, playState, videoSize, playerError ->
        LiveState.PlayerState(
            playState = playState,
            videoSize = videoSize,
            playerError = playerError,
            player = player
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LiveState.PlayerState()
        )

    override fun onEvent(event: LiveEvent) {
        when (event) {
            LiveEvent.OpenDlnaDevices -> openDlnaDevices()
            LiveEvent.CloseDlnaDevices -> closeDlnaDevices()
            is LiveEvent.ConnectDlnaDevice -> connectDlnaDevice(event.device)
            is LiveEvent.DisconnectDlnaDevice -> disconnectDlnaDevice(event.device)
            LiveEvent.Record -> record()
            is LiveEvent.OnFavourite -> onFavourite(event.url)
            LiveEvent.Stop -> stop()
            is LiveEvent.OnVolume -> onVolume(event.volume)
        }
    }

    private val _isDevicesVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isDevicesVisible = _isDevicesVisible.asStateFlow()

    private val _searching: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    private fun openDlnaDevices() {
        DLNACastManager.bindCastService(application)
        DLNACastManager.registerDeviceListener(this)
        viewModelScope.launch {
            delay(800.milliseconds)
            _searching.value = true
        }
        _isDevicesVisible.value = true
    }

    private fun closeDlnaDevices() {
        _searching.value = false
        _isDevicesVisible.value = false
        _devices.value = emptyList()
        DLNACastManager.unbindCastService(application)
        DLNACastManager.unregisterListener(this)
    }

    private var controlPoint: DeviceControl? = null

    private fun connectDlnaDevice(device: Device<*, *, *>) {
        controlPoint = DLNACastManager.connectDevice(device, this)
    }

    private fun disconnectDlnaDevice(device: Device<*, *, *>) {
        controlPoint?.stop()
        DLNACastManager.disconnectDevice(device)
    }

    private fun record() {
        writable.update {
            it.copy(
                recording = !readable.recording
            )
        }
    }

    private fun onFavourite(url: String) {
        viewModelScope.launch {
            val live = liveRepository.getByUrl(url) ?: return@launch
            val id = live.id
            val target = !live.favourite
            liveRepository.setFavourite(id, target)
        }
    }

    private fun onVolume(target: Float) {
        _volume.update { target }

        playerState.value.player?.volume = target
        controlPoint?.setVolume((target * 100).roundToInt(), null)
    }

    override fun onDeviceAdded(device: Device<*, *, *>) {
        _devices.update { it + device }
    }

    override fun onDeviceRemoved(device: Device<*, *, *>) {
        _devices.update { it - device }
    }

    override fun onConnected(device: Device<*, *, *>) {
        writable.update { it.copy(connected = device) }
        val url = metadata.value.live?.url ?: return
        val title = metadata.value.live?.title.orEmpty()

        controlPoint?.setAVTransportURI(
            uri = url,
            title = title,
            callback = object : ServiceActionCallback<Unit> {
                override fun onSuccess(result: Unit) {
                    logger.log("ok: url=$url, title=$title")
                    controlPoint?.play()
                }

                override fun onFailure(msg: String) {
                    logger.log(msg)
                }
            }
        )
    }

    override fun onDisconnected(device: Device<*, *, *>) {
        writable.update { it.copy(connected = null) }
        controlPoint?.stop()
        controlPoint = null
    }

    private fun stop() {
        playerManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.stop()
    }
}