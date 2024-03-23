package com.m3u.features.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.prefix
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.PlayerManagerV2
import com.m3u.data.service.selectedFormats
import com.m3u.data.service.trackFormats
import com.m3u.dlna.DLNACastManager
import com.m3u.dlna.OnDeviceRegistryListener
import com.m3u.dlna.control.DeviceControl
import com.m3u.dlna.control.OnDeviceControlListener
import com.m3u.dlna.control.ServiceActionCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
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
import org.jupnp.model.meta.Device
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class StreamViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val playerManager: PlayerManagerV2,
    before: Logger,
) : ViewModel(), OnDeviceRegistryListener, OnDeviceControlListener {
    private val logger = before.prefix("feat-stream")
    private val _devices = MutableStateFlow<ImmutableList<Device<*, *, *>>>(persistentListOf())

    // searched screencast devices
    internal val devices = _devices.asStateFlow()

    private val _volume: MutableStateFlow<Float> = MutableStateFlow(1f)
    internal val volume = _volume.asStateFlow()

    internal val stream: StateFlow<Stream?> = playerManager.stream
    internal val playlist: StateFlow<Playlist?> = playerManager.playlist

    internal val isSeriesPlaylist: StateFlow<Boolean> = playerManager
        .playlist
        .map { it?.type == DataSource.Xtream.TYPE_SERIES }
        .stateIn(
            scope = viewModelScope,
            initialValue = false,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val formats: StateFlow<ImmutableMap<Int, ImmutableList<Format>>> =
        playerManager
            .trackFormats
            .map { all ->
                all
                    .mapValues { (_, formats) -> formats.toPersistentList() }
                    .toImmutableMap()
            }
            .stateIn(
                scope = viewModelScope,
                initialValue = persistentMapOf(),
                started = SharingStarted.WhileSubscribed(5_000L)
            )

    internal val selectedFormats: StateFlow<ImmutableMap<@C.TrackType Int, Format?>> =
        playerManager
            .selectedFormats
            .map { all -> all.toPersistentMap() }
            .stateIn(
                scope = viewModelScope,
                initialValue = persistentMapOf(),
                started = SharingStarted.WhileSubscribed(5_000L)
            )

    internal fun chooseTrack(type: @C.TrackType Int, format: Format) {
        val groups = playerManager.tracksGroups.value
        val group = groups.find { it.type == type } ?: return
        val trackGroup = group.mediaTrackGroup
        for (index in 0 until trackGroup.length) {
            if (trackGroup.getFormat(index).id == format.id) {
                playerManager.chooseTrack(
                    group = trackGroup,
                    index = index
                )
                break
            }
        }
    }

    internal fun clearTrack(type: @C.TrackType Int) {
        playerManager.clearTrack(type)
    }

    // stream playing state
    internal val playerState: StateFlow<PlayerState> = combine(
        playerManager.player,
        playerManager.playbackState,
        playerManager.size,
        playerManager.playbackException
    ) { player, playState, videoSize, playbackException ->
        PlayerState(
            playState = playState,
            videoSize = videoSize,
            playerError = playbackException,
            player = player
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerState()
        )

    private val _isDevicesVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // show searching devices dialog or not
    internal val isDevicesVisible = _isDevicesVisible.asStateFlow()

    private val _searching: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // searching or not
    internal val searching = _searching.asStateFlow()

    private val _connected = MutableStateFlow<Device<*, *, *>?>(null)
    internal val connected = _connected.asStateFlow()

    internal fun openDlnaDevices() {
        try {
            DLNACastManager.registerDeviceListener(this)
        } catch (ignore: Exception) {

        }
        viewModelScope.launch {
            delay(800.milliseconds)
            _searching.value = true
        }
        _isDevicesVisible.value = true
    }

    internal fun closeDlnaDevices() {
        try {
            _searching.value = false
            _isDevicesVisible.value = false
            _devices.value = persistentListOf()
            DLNACastManager.unregisterListener(this)
        } catch (ignore: Exception) {

        }
    }

    private var controlPoint: DeviceControl? = null

    internal fun connectDlnaDevice(device: Device<*, *, *>) {
        controlPoint = DLNACastManager.connectDevice(device, this)
    }

    internal fun disconnectDlnaDevice(device: Device<*, *, *>) {
        controlPoint?.stop()
        controlPoint = null
        DLNACastManager.disconnectDevice(device)
    }

    internal fun onFavourite() {
        viewModelScope.launch {
            val stream = this@StreamViewModel.stream.value ?: return@launch
            val id = stream.id
            val target = !stream.favourite
            streamRepository.setFavourite(id, target)
        }
    }

    internal fun onVolume(target: Float) {
        _volume.update { target }

        playerState.value.player?.volume = target
        controlPoint?.setVolume((target * 100).roundToInt(), null)
    }

    override fun onDeviceAdded(device: Device<*, *, *>) {
        _devices.update { (it + device).toPersistentList() }
    }

    override fun onDeviceRemoved(device: Device<*, *, *>) {
        _devices.update { (it - device).toPersistentList() }
    }

    override fun onConnected(device: Device<*, *, *>) {
        _connected.value = device
        val url = stream.value?.url ?: return
        val title = stream.value?.title.orEmpty()

        controlPoint?.setAVTransportURI(
            uri = url,
            title = title,
            callback = object : ServiceActionCallback<Unit> {
                override fun onSuccess(result: Unit) {
                    controlPoint?.play()
                }

                override fun onFailure(msg: String) {
                    logger.log(msg)
                }
            }
        )
    }

    override fun onDisconnected(device: Device<*, *, *>) {
        _connected.value = null
        controlPoint?.stop()
        controlPoint = null
    }

    fun release() {
        try {
            controlPoint?.stop()
            controlPoint = null
            playerManager.release()
            DLNACastManager.unregisterListener(this)
        } catch (ignored: Exception) {

        }
    }

    internal fun openInExternalPlayer() {

    }
}