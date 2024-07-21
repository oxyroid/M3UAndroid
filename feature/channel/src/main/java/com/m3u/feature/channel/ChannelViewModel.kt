package com.m3u.feature.channel

import android.annotation.SuppressLint
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.await
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.currentTracks
import com.m3u.data.service.tracks
import com.m3u.data.worker.ProgrammeReminder
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.datetime.Clock
import net.mm2d.upnp.ControlPoint
import net.mm2d.upnp.ControlPointFactory
import net.mm2d.upnp.Device
import javax.inject.Inject
import kotlin.math.roundToInt
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
    delegate: Logger,
) : ViewModel(), ControlPoint.DiscoveryListener {
    private val logger = delegate.install(Profiles.VIEWMODEL_CHANNEL)

    // searched screencast devices
    internal var devices by mutableStateOf(emptyList<Device>())

    private val _volume: MutableStateFlow<Float> by lazy {
        MutableStateFlow(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / 100f)
    }
    internal val volume = _volume.asStateFlow()

    internal val channel: StateFlow<Channel?> = playerManager.channel
    internal val playlist: StateFlow<Playlist?> = playerManager.playlist

    internal val isSeriesPlaylist: Flow<Boolean> = playlist.map { it?.isSeries ?: false }

    internal val isProgrammeSupported: Flow<Boolean> = playlist.map {
        it ?: return@map false
        if (it.isSeries || it.isVod) return@map false
        when (it.source) {
            DataSource.Xtream -> true
            DataSource.M3U -> it.epgUrls.isNotEmpty()
            else -> false
        }
    }

    internal val tracks: Flow<Map<Int, List<Format>>> = playerManager.tracks
        .map { all ->
            all
                .mapValues { (_, formats) -> formats }
                .toMap()
        }

    internal val currentTracks: Flow<Map<@C.TrackType Int, Format?>> = playerManager.currentTracks

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

    // channel playing state
    internal val playerState: StateFlow<PlayerState> = combine(
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

    internal fun openDlnaDevices() {
        viewModelScope.launch {
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

    internal fun closeDlnaDevices() {
        runCatching {
            _searching.value = false
            _isDevicesVisible.value = false

            controlPoint?.removeDiscoveryListener(this)
            controlPoint?.stop()
            controlPoint?.terminate()
            controlPoint = null

            devices = emptyList()
        }
    }

    override fun onDiscover(device: Device) {
        devices = devices + device
    }

    override fun onLost(device: Device) {
        devices = devices - device
    }

    private var controlPoint: ControlPoint? = null

    internal fun connectDlnaDevice(device: Device) {
        val url = channel.value?.url ?: return
        device.findAction(ACTION_SET_AV_TRANSPORT_URI)?.invoke(
            argumentValues = mapOf(
                INSTANCE_ID to "0",
                CURRENT_URI to url
            )
        )
    }

    internal fun disconnectDlnaDevice(device: Device) {

    }

    internal fun onFavourite() {
        viewModelScope.launch {
            val id = channel.value?.id ?: return@launch
            channelRepository.favouriteOrUnfavourite(id)
        }
    }

    internal fun onVolume(target: Float) {
        _volume.update { target }

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (target * 100).roundToInt(),
            AudioManager.FLAG_VIBRATE
        )

//        controlPoint?.setVolume((target * 100).roundToInt(), null)
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

    internal val programmeReminderIds: StateFlow<List<Int>> = workManager.getWorkInfosFlow(
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
        viewModelScope.launch {
            val infos = workManager
                .getWorkInfos(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
                .await()
                .filter { ProgrammeReminder.TAG in it.tags }
                .filter { info -> ProgrammeReminder.readProgrammeId(info.tags) == programme.id }
            infos.forEach {
                workManager.cancelWorkById(it.id)
            }
        }
    }

    // the channels which is in the same category with the current channel
    // or the episodes which is in the same series.
    internal val channels: Flow<PagingData<Channel>> = playlist.flatMapLatest { playlist ->
        playlist ?: return@flatMapLatest flowOf(PagingData.empty())
        Pager(PagingConfig(10)) {
            channelRepository.pagingAllByPlaylistUrl(
                playlist.url,
                channel.value?.category.orEmpty(),
                "",
                ChannelRepository.Sort.UNSPECIFIED
            )
        }
            .flow
    }
        .cachedIn(viewModelScope)

    internal val programmes: Flow<PagingData<Programme>> = channel.flatMapLatest { channel ->
        channel ?: return@flatMapLatest flowOf(PagingData.empty())
        val originalId = channel.originalId ?: return@flatMapLatest flowOf(PagingData.empty())
        val playlist = channel.playlistUrl.let { playlistRepository.get(it) }
        playlist ?: return@flatMapLatest flowOf(PagingData.empty())
        val epgUrls = playlist.epgUrlsOrXtreamXmlUrl()
        Pager(PagingConfig(15)) {
            programmeRepository.pagingByEpgUrlsAndOriginalId(
                epgUrls = epgUrls,
                originalId = originalId
            )
        }
            .flow
    }
        .cachedIn(viewModelScope)

    private val defaultProgrammeRange: ProgrammeRange
        get() = with(Clock.System.now()) {
            ProgrammeRange(
                this.minus(2.hours).toEpochMilliseconds(),
                this.plus(6.hours).toEpochMilliseconds()
            )
        }

    internal val programmeRange: StateFlow<ProgrammeRange> = channel.flatMapLatest { channel ->
        channel ?: return@flatMapLatest flowOf(defaultProgrammeRange)
        val originalId = channel.originalId ?: return@flatMapLatest flowOf(defaultProgrammeRange)
        programmeRepository
            .observeProgrammeRange(channel.playlistUrl, originalId)
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

    companion object {
        private const val ACTION_SET_AV_TRANSPORT_URI = "SetAVTransportURI"
        private const val ACTION_PLAY = "Play"
        private const val ACTION_PAUSE = "Pause"
        private const val ACTION_STOP = "Stop"

        private const val INSTANCE_ID = "InstanceID"
        private const val CURRENT_URI = "CurrentURI"
        private const val CURRENT_URI_META_DATA = "CurrentURIMetaData"
    }
}