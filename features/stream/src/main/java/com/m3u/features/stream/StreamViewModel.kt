package com.m3u.features.stream

import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.Stream
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.database.model.type
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.repository.stream.StreamRepository
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.selectedFormats
import com.m3u.data.service.trackFormats
import com.m3u.dlna.DLNACastManager
import com.m3u.dlna.OnDeviceRegistryListener
import com.m3u.dlna.control.DeviceControl
import com.m3u.dlna.control.OnDeviceControlListener
import com.m3u.dlna.control.ServiceActionCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val playlistRepository: PlaylistRepository,
    private val streamRepository: StreamRepository,
    private val playerManager: PlayerManager,
    private val audioManager: AudioManager,
    private val programmeRepository: ProgrammeRepository,
    delegate: Logger,
    private val messager: Messager,
    @Dispatcher(Main) private val mainDispatcher: CoroutineDispatcher
) : ViewModel(), OnDeviceRegistryListener, OnDeviceControlListener {
    private val logger = delegate.install(Profiles.VIEWMODEL_STREAM)
    private val _devices = MutableStateFlow<List<Device<*, *, *>>>(emptyList())

    // searched screencast devices
    internal val devices = _devices.asStateFlow()

    private val _volume: MutableStateFlow<Float> by lazy {
        MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / 100f)
    }
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

    internal val formats: StateFlow<Map<Int, List<Format>>> =
        playerManager
            .trackFormats
            .map { all ->
                all
                    .mapValues { (_, formats) -> formats }
                    .toMap()
            }
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyMap(),
                started = SharingStarted.WhileSubscribed(5_000L)
            )

    internal val selectedFormats: StateFlow<Map<@C.TrackType Int, Format?>> =
        playerManager
            .selectedFormats
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyMap(),
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
            _devices.value = emptyList()
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
            val id = stream.value?.id ?: return@launch
            streamRepository.favouriteOrUnfavourite(id)
        }
    }

    internal fun onVolume(target: Float) {
        _volume.update { target }

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (target * 100).roundToInt(),
            AudioManager.FLAG_VIBRATE
        )

        controlPoint?.setVolume((target * 100).roundToInt(), null)
    }

    override fun onDeviceAdded(device: Device<*, *, *>) {
        _devices.update { (it + device) }
    }

    override fun onDeviceRemoved(device: Device<*, *, *>) {
        _devices.update { (it - device) }
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

    fun destroy() {
        try {
            controlPoint?.stop()
            controlPoint = null
            playerManager.release()
            DLNACastManager.unregisterListener(this)
        } catch (ignored: Exception) {

        }
    }

    fun pauseOrContinue(isContinued: Boolean) {
        playerManager.pauseOrContinue(isContinued)
    }

    internal val neighboring: Flow<PagingData<Stream>> = playlist.flatMapLatest { playlist ->
        Pager(PagingConfig(10)) {
            streamRepository.pagingAllByPlaylistUrl(
                playlist?.url.orEmpty(),
                "",
                StreamRepository.Sort.UNSPECIFIED
            )
        }
            .flow
            .cachedIn(viewModelScope)
    }

    internal val programmes: Flow<PagingData<Programme>> = stream.flatMapLatest { stream ->
        stream ?: return@flatMapLatest flowOf()
        val channelId = stream.channelId ?: return@flatMapLatest flowOf()
        val playlist = stream.playlistUrl.let { playlistRepository.get(it) }
        playlist ?: return@flatMapLatest flowOf()
        val epgUrls = playlist.epgUrlsOrXtreamXmlUrl()
        Pager(PagingConfig(15)) {
            programmeRepository.pagingByEpgUrlsAndChannelId(
                epgUrls = epgUrls,
                channelId = channelId
            )
        }
            .flow
            .cachedIn(viewModelScope)
    }

    internal val timelineHourRange: StateFlow<IntRange> = stream.flatMapLatest { stream ->
        stream ?: return@flatMapLatest flowOf()
        val channelId = stream.channelId ?: return@flatMapLatest flowOf()
        val playlist = stream.playlistUrl.let { playlistRepository.get(it) }
        playlist ?: return@flatMapLatest flowOf()
        val epgUrls = playlist.epgUrlsOrXtreamXmlUrl()
        programmeRepository
            .observeTimeHourRange(epgUrls, channelId)
            .map {
                if (it.isEmpty()) 0..24
                else it
            }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = 0..24,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal val isEpgRefreshing: StateFlow<Boolean> = combine(
        playlist,
        programmeRepository.refreshingEpgUrls
    ) { playlist, refreshingEpgUrls ->
        val epgUrls = playlist?.epgUrlsOrXtreamXmlUrl() ?: emptyList()
        epgUrls.any { epgUrl -> epgUrl in refreshingEpgUrls }
    }
        .stateIn(
            scope = viewModelScope,
            // disable refresh button by default
            initialValue = true,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    internal fun checkOrRefreshProgrammes(ignoreCache: Boolean = false) {
        viewModelScope.launch {
            val stream = stream.value ?: return@launch
            val result = runCatching {
                // TODO: call it in worker.
                programmeRepository.checkOrRefreshProgrammesOrThrow(
                    playlistUrl = stream.playlistUrl,
                    ignoreCache = ignoreCache
                )
            }
            if (result.isFailure) {
                messager.emit(result.exceptionOrNull()?.message.orEmpty())
                logger.log(result.exceptionOrNull()?.message.orEmpty())
                return@launch
            }
        }
    }

    internal fun onKeyCode(code: TelevisionKeyCode) {
        when (code) {
            TelevisionKeyCode.UP -> {}
            TelevisionKeyCode.DOWN -> {}
            TelevisionKeyCode.LEFT -> {
                val player = playerState.value.player ?: return
                viewModelScope.launch(mainDispatcher) {
                    player.seekTo(player.currentPosition - 15000)
                }
            }

            TelevisionKeyCode.RIGHT -> {
                val player = playerState.value.player ?: return
                viewModelScope.launch(mainDispatcher) {
                    player.seekTo(player.currentPosition + 15000)
                }
            }
        }
    }
}