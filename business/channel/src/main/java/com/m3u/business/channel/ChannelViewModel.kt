package com.m3u.business.channel

import android.annotation.SuppressLint
import android.media.AudioManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.util.coroutine.flatmapCombined
import com.m3u.core.wrapper.Sort
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.TrackOption
import com.m3u.data.service.trackOptions
import com.m3u.data.worker.ProgrammeReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mm2d.upnp.ControlPoint
import net.mm2d.upnp.ControlPointFactory
import net.mm2d.upnp.Device
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val playerManager: PlayerManager,
    private val audioManager: AudioManager,
    private val programmeRepository: ProgrammeRepository,
    private val workManager: WorkManager,
) : ViewModel(), ControlPoint.DiscoveryListener {

    /**
     * it is not real-time position but last played position.
     */
    var cwPosition: Long by mutableLongStateOf(-1L)
        private set

    private val cwPositionConsumer = CwPositionConsumer(
        flow = playerManager.cwPosition,
        coroutineScope = viewModelScope
    ) {
        this@ChannelViewModel.cwPosition = it
    }

    fun onResetPlayback() {
        val channelUrl = channel.value?.url ?: return
        viewModelScope.launch {
            playerManager.onResetPlayback(channelUrl)
        }
    }

    fun onMaskStateChanged(visible: Boolean) {
        if (!visible) {
            cwPositionConsumer.notifyConsumed()
        }
    }


    // searched screencast devices
    var devices by mutableStateOf(emptyList<Device>())

    private val _volume: MutableStateFlow<Float> by lazy {
        MutableStateFlow(
            with(audioManager) {
                getStreamVolume(AudioManager.STREAM_MUSIC) * 1f / getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }
        )
    }
    val volume = _volume.asStateFlow()

    val channel: StateFlow<Channel?> = playerManager.channel
    val playlist: StateFlow<Playlist?> = playerManager.playlist

    val adjacentChannels: StateFlow<AdjacentChannels?> = flatmapCombined(
        playlist,
        channel
    ) { playlist, channel ->
        playlist ?: return@flatmapCombined flowOf(null)
        channel ?: return@flatmapCombined flowOf(null)
        channelRepository.observeAdjacentChannels(
            channelId = channel.id,
            playlistUrl = playlist.url,
            category = channel.category
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )


    val isSeriesPlaylist: Flow<Boolean> = playlist.map { it?.isSeries ?: false }

    val isProgrammeSupported: Flow<Boolean> = playlist.map {
        it ?: return@map false
        if (it.isSeries || it.isVod) return@map false
        when (it.source) {
            DataSource.Xtream -> true
            DataSource.M3U -> it.epgUrls.isNotEmpty()
            else -> false
        }
    }

    val tracks: Flow<Map<Int, List<TrackOption>>> = playerManager.trackOptions
        .map { all ->
            all.mapValues { (_, options) -> options }
        }

    fun chooseTrack(option: TrackOption) {
        playerManager.chooseTrack(
            groupIndex = option.groupIndex,
            trackIndex = option.trackIndex
        )
    }

    fun clearTrack(type: @C.TrackType Int) {
        playerManager.clearTrack(type)
    }

    // channel playing state
    private val basePlayerState: Flow<PlayerState> = combine(
        playerManager.player,
        playerManager.playbackState,
        playerManager.size,
        playerManager.playbackException,
        playerManager.isPlaying
    ) { player, playState, videoSize, playbackException, isPlaying ->
        PlayerState(
            playState = playState,
            videoSize = videoSize,
            playerError = playbackException,
            player = player,
            isPlaying = isPlaying
        )
    }

    val playerState: StateFlow<PlayerState> = combine(
        basePlayerState,
        playerManager.streamMetadata
    ) { state, streamMetadata ->
        state.copy(streamMetadata = streamMetadata)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerState()
        )

    private val _isDevicesVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // show searching devices dialog or not
    val isDevicesVisible = _isDevicesVisible.asStateFlow()

    private val _searching: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // searching or not
    val searching = _searching.asStateFlow()

    fun openDlnaDevices() {
        stopDlnaSearch(clearDevices = false)
        dlnaSearchJob = viewModelScope.launch {
            delay(800.milliseconds)
            _searching.value = true
            controlPoint = ControlPointFactory.create().apply {
                addDiscoveryListener(this@ChannelViewModel)
                initialize()
                start()
                search()
            }
        }
        _isDevicesVisible.value = true
    }

    fun closeDlnaDevices() {
        runCatching {
            _searching.value = false
            _isDevicesVisible.value = false
            stopDlnaSearch(clearDevices = true)
        }
    }

    override fun onDiscover(device: Device) {
        devices = (devices.filterNot { it.hasSameIdentity(device) } + device)
            .sortedWith(compareBy<Device> { it.friendlyName.lowercase() }.thenBy { it.deviceKey })
    }

    override fun onLost(device: Device) {
        devices = devices.filterNot { it.hasSameIdentity(device) }
    }

    private var controlPoint: ControlPoint? = null
    private var dlnaSearchJob: Job? = null

    fun connectDlnaDevice(device: Device) {
        val url = channel.value?.url?.substringBefore('|') ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                device.findAction(ACTION_SET_AV_TRANSPORT_URI)?.invokeSync(
                    mapOf(
                        INSTANCE_ID to "0",
                        CURRENT_URI to url,
                        CURRENT_URI_META_DATA to ""
                    ),
                    false
                )
                device.findAction(ACTION_PLAY)?.invokeSync(
                    mapOf(
                        INSTANCE_ID to "0",
                        SPEED to "1"
                    ),
                    false
                )
            }
        }
    }

    fun disconnectDlnaDevice(device: Device) {

    }

    fun onFavorite() {
        viewModelScope.launch {
            val id = channel.value?.id ?: return@launch
            channelRepository.favouriteOrUnfavourite(id)
        }
    }

    fun onVolume(target: Float) {
        _volume.update { target }
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (target * maxVolume).roundToInt(),
            AudioManager.FLAG_VIBRATE
        )

//        controlPoint?.setVolume((target * 100).roundToInt(), null)
    }

    fun getPreviousChannel() {
        viewModelScope.launch {
            val previousChannelId = adjacentChannels.value?.prevId
            if (adjacentChannels.value != null && previousChannelId != null) {
                playerManager.play(MediaCommand.Common(previousChannelId))
            }
        }
    }

    fun getNextChannel() {
        viewModelScope.launch {
            val nextChannelId = adjacentChannels.value?.nextId
            if (adjacentChannels.value != null && nextChannelId != null) {
                playerManager.play(MediaCommand.Common(nextChannelId))
            }
        }
    }

    fun destroy() {
        runCatching {
            controlPoint?.removeDiscoveryListener(this)
            controlPoint?.stop()
            controlPoint?.terminate()
            controlPoint = null

            playerManager.release()
        }
    }

    fun pauseOrContinue(isContinued: Boolean) {
        playerManager.pauseOrContinue(isContinued)
    }

    val programmeReminderIds: StateFlow<List<Int>> = workManager.getWorkInfosFlow(
        WorkQuery.fromStates(
            WorkInfo.State.ENQUEUED
        )
    )
        .map { infos: List<WorkInfo> ->
            infos
                .filter { ProgrammeReminder.TAG in it.tags }
                .mapNotNull { info -> ProgrammeReminder.readProgrammeId(info.tags) }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.Lazily
        )

    fun onRemindProgramme(programme: Programme) {
        ProgrammeReminder(
            workManager = workManager,
            programmeId = programme.id,
            programmeStart = programme.start
        )
    }

    @SuppressLint("RestrictedApi")
    fun onCancelRemindProgramme(programme: Programme) {
        viewModelScope.launch(Dispatchers.IO) {
            val infos = workManager
                .getWorkInfos(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
                .get()
                .filter { ProgrammeReminder.TAG in it.tags }
                .filter { info -> ProgrammeReminder.readProgrammeId(info.tags) == programme.id }
            infos.forEach {
                workManager.cancelWorkById(it.id)
            }
        }
    }

    // the channels which is in the same category with the current channel
    // or the episodes which is in the same series.
    val pagingChannels: Flow<PagingData<Channel>> = playlist.flatMapLatest { playlist ->
        playlist ?: return@flatMapLatest flowOf(PagingData.empty())
        Pager(PagingConfig(10)) {
            channelRepository.pagingAllByPlaylistUrl(
                playlist.url,
                channel.value?.category.orEmpty(),
                "",
                Sort.UNSPECIFIED
            )
        }
            .flow
    }
        .cachedIn(viewModelScope)

    val programmes: Flow<PagingData<Programme>> = channel.flatMapLatest { channel ->
        channel ?: return@flatMapLatest flowOf(PagingData.empty())
        val relationId = channel.relationId?.takeIf { it.isNotBlank() } ?: channel.title
        val playlist = channel.playlistUrl.let { playlistRepository.get(it) }
        playlist ?: return@flatMapLatest flowOf(PagingData.empty())
        programmeRepository.pagingProgrammes(
            playlistUrl = playlist.url,
            relationId = relationId
        )
            .cachedIn(viewModelScope)
    }

    val currentProgramme: StateFlow<Programme?> = channel.flatMapLatest { channel ->
        channel ?: return@flatMapLatest flowOf<Programme?>(null)
        flow<Programme?> {
            while (true) {
                emit(programmeRepository.getProgrammeCurrently(channel.id))
                delay(1.minutes)
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    private val defaultProgrammeRange: ProgrammeRange
        get() = with(Clock.System.now()) {
            ProgrammeRange(
                this.minus(2.hours).toEpochMilliseconds(),
                this.plus(6.hours).toEpochMilliseconds()
            )
        }

    val programmeRange: StateFlow<ProgrammeRange> = channel.flatMapLatest { channel ->
        channel ?: return@flatMapLatest flowOf(defaultProgrammeRange)
        val relationId = channel.relationId?.takeIf { it.isNotBlank() } ?: channel.title
        programmeRepository
            .observeProgrammeRange(channel.playlistUrl, relationId)
            .map {
                it
                    .spread(ProgrammeRange.Spread.Increase(5.minutes, 1.hours + 5.minutes))
                    .spread(ProgrammeRange.Spread.Absolute(8.hours))
            }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = defaultProgrammeRange,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun onSpeedUpdated(race: Float) {
        playerManager.updateSpeed(race)
    }

    fun recordVideo(uri: Uri) {
        viewModelScope.launch {
            playerManager.recordVideo(uri)
        }
    }

    private fun stopDlnaSearch(clearDevices: Boolean) {
        dlnaSearchJob?.cancel()
        dlnaSearchJob = null
        controlPoint?.removeDiscoveryListener(this)
        controlPoint?.stop()
        controlPoint?.terminate()
        controlPoint = null
        if (clearDevices) {
            devices = emptyList()
        }
    }

    private fun Device.hasSameIdentity(other: Device): Boolean {
        return deviceKey == other.deviceKey
    }

    private val Device.deviceKey: String
        get() = sequenceOf(
            udn,
            location,
            "$ipAddress|$friendlyName|$deviceType"
        )
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    companion object {
        private const val ACTION_SET_AV_TRANSPORT_URI = "SetAVTransportURI"
        private const val ACTION_PLAY = "Play"
        private const val ACTION_PAUSE = "Pause"
        private const val ACTION_STOP = "Stop"

        private const val INSTANCE_ID = "InstanceID"
        private const val CURRENT_URI = "CurrentURI"
        private const val CURRENT_URI_META_DATA = "CurrentURIMetaData"
        private const val SPEED = "Speed"
    }
}
