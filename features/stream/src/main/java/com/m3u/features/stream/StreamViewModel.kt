package com.m3u.features.stream

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.Message
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fourthline.cling.model.meta.Device
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    playlistRepository: PlaylistRepository,
    private val playerManager: PlayerManager,
    private val logger: Logger,
    private val application: Application
) : BaseViewModel<StreamState, StreamEvent, Message.Static>(
    emptyState = StreamState()
), OnDeviceRegistryListener, OnDeviceControlListener {
    private val _devices = MutableStateFlow<List<Device<*, *, *>>>(emptyList())

    // searched screencast devices
    val devices = _devices.asStateFlow()

    private val _volume: MutableStateFlow<Float> = MutableStateFlow(1f)
    val volume = _volume.asStateFlow()

    // playlist and stream info
    val metadata: StateFlow<StreamState.Metadata> = combine(
        playerManager.url,
        streamRepository.observeAll(),
        playlistRepository.observeAll()
    ) { url, streams, playlists ->
        val stream = streams.find { it.url == url }
        val playlist = playlists.find { it.url == stream?.playlistUrl }
        StreamState.Metadata(playlist, stream)
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = StreamState.Metadata(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // stream playing state
    val playerState: StateFlow<StreamState.PlayerState> = combine(
        playerManager.observe(),
        playerManager.playbackState,
        playerManager.videoSize,
        playerManager.playerError
    ) { player, playState, videoSize, playerError ->
        StreamState.PlayerState(
            playState = playState,
            videoSize = videoSize,
            playerError = playerError,
            player = player
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StreamState.PlayerState()
        )
    init {
        playerManager
            .url
            .onEach { url ->
                url?: return@onEach
                val stream = streamRepository.getByUrl(url)?: return@onEach
                streamRepository.reportPlayed(stream.id)
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: StreamEvent) {
        when (event) {
            StreamEvent.OpenDlnaDevices -> openDlnaDevices()
            StreamEvent.CloseDlnaDevices -> closeDlnaDevices()
            is StreamEvent.ConnectDlnaDevice -> connectDlnaDevice(event.device)
            is StreamEvent.DisconnectDlnaDevice -> disconnectDlnaDevice(event.device)
            StreamEvent.Record -> record()
            is StreamEvent.OnFavourite -> onFavourite(event.url)
            StreamEvent.Stop -> stop()
            is StreamEvent.OnVolume -> onVolume(event.volume)
        }
    }

    private val _isDevicesVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // show searching devices dialog or not
    val isDevicesVisible = _isDevicesVisible.asStateFlow()

    private val _searching: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // searching or not
    val searching = _searching.asStateFlow()

    private fun openDlnaDevices() {
        DLNACastManager.bindCastService(application)
        binded = true
        DLNACastManager.registerDeviceListener(this)
        viewModelScope.launch {
            delay(800.milliseconds)
            _searching.value = true
        }
        _isDevicesVisible.value = true
    }

    @Volatile
    private var binded = false

    private fun closeDlnaDevices() {
        if (!binded) return
        binded = false
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
            val stream = streamRepository.getByUrl(url) ?: return@launch
            val id = stream.id
            val target = !stream.favourite
            streamRepository.setFavourite(id, target)
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
        val url = metadata.value.stream?.url ?: return
        val title = metadata.value.stream?.title.orEmpty()

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
        closeDlnaDevices()
        playerManager.stop()
        controlPoint?.stop()
        controlPoint = null
        super.onCleared()
    }
}