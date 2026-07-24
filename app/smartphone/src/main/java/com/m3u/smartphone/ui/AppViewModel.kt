package com.m3u.smartphone.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertHeaderItem
import androidx.paging.map as pagingMap
import androidx.work.WorkManager
import com.m3u.business.playlist.ChannelWithProgramme
import com.m3u.data.api.TvApiDelegate
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.extension.ExtensionContributionRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.tv.ConnectionToTvValue
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.data.tv.model.TvInfo
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.smartphone.ui.common.connect.RemoteControlSheetValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class)
class AppViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val extensionContributionRepository: ExtensionContributionRepository,
    private val workManager: WorkManager,
    private val tvRepository: TvRepository,
    private val tvApi: TvApiDelegate,
) : ViewModel() {
    var searchQuery = mutableStateOf("")

    private val searchQueries = snapshotFlow { searchQuery.value }
        .map(String::trim)
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val localSearchResults: Flow<PagingData<ChannelWithProgramme>> = searchQueries
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        prefetchDistance = 5
                    ),
                    pagingSourceFactory = { channelRepository.search(query) }
                )
                    .flow
                    .map { data ->
                        data.pagingMap { channel ->
                            ChannelWithProgramme(
                                channel = channel,
                                programme = null
                            )
                        }
                    }
            }
        }

    private val extensionSearchResults = searchQueries
        .mapLatestSearchResults { query ->
            val contributions = extensionContributionRepository.search(query, SEARCH_RESULT_LIMIT)
            contributions
                .map { contribution -> contribution.channel }
                .distinctBy { channel -> channel.id }
        }
        .flowOn(Dispatchers.IO)

    val channels: Flow<PagingData<ChannelWithProgramme>> = combine(
        searchQueries,
        localSearchResults,
        extensionSearchResults,
    ) { query, local, extensionResults ->
        val promotedChannels = extensionResults.itemsFor(query)
        val promotedIds = promotedChannels.mapTo(mutableSetOf()) { channel -> channel.id }
        promotedChannels.asReversed().fold(
            local.filter { item -> item.channel.id !in promotedIds }
        ) { paging, channel ->
            paging.insertHeaderItem(
                item = ChannelWithProgramme(channel = channel, programme = null)
            )
        }
    }.cachedIn(viewModelScope)

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
    private var connectedTv by mutableStateOf<TvInfo?>(null)
    private var connectionToTvValue by mutableStateOf<ConnectionToTvValue>(ConnectionToTvValue.Idle())
    private var connectToTvJob: Job? = null
    private val timber = Timber.tag("RemoteControlVM")

    init {
        refreshProgrammes()
        tvRepository.connected
            .onEach {
                timber.d("connected tv changed: $it")
                connectedTv = it
            }
            .launchIn(viewModelScope)
    }

    val remoteControlSheetValue: RemoteControlSheetValue
        get() = connectedTv
            ?.let { RemoteControlSheetValue.DPad(it) }
            ?: RemoteControlSheetValue.Prepare(
                code = code,
                searchingOrConnecting = connectionToTvValue is ConnectionToTvValue.Searching ||
                        connectionToTvValue is ConnectionToTvValue.Connecting,
                subtitle = (connectionToTvValue as? ConnectionToTvValue.Idle)?.reason,
                timedOut = connectionToTvValue is ConnectionToTvValue.Timeout
            )

    fun checkTvCodeOnSmartphone() {
        val pin = code.toIntOrNull() ?: return
        timber.d("connect clicked, pin=$pin, previousState=${connectionToTvValue::class.simpleName}")
        connectToTvJob?.cancel()
        connectToTvJob = tvRepository
            .connectToTv(pin)
            .onEach { value ->
                timber.d("connect state: ${value::class.simpleName}, value=$value")
                connectionToTvValue = value
            }
            .launchIn(viewModelScope)
    }

    fun forgetTvCodeOnSmartphone() {
        timber.d("forget tv code")
        code = ""
        connectionToTvValue = ConnectionToTvValue.Idle()
        connectToTvJob?.cancel()
        connectToTvJob = null
        viewModelScope.launch { tvRepository.disconnectToTv() }
    }

    fun onRemoteDirection(direction: RemoteDirection) {
        timber.d("remote direction: $direction")
        viewModelScope.launch { tvApi.remoteDirection(direction.value) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val SEARCH_RESULT_LIMIT = 50
    }
}

internal fun <T> Flow<String>.mapLatestSearchResults(
    search: suspend (query: String) -> List<T>,
): Flow<QuerySearchResults<T>> = flatMapLatest { query ->
    flow {
        emit(QuerySearchResults(query, emptyList()))
        if (query.isNotBlank()) {
            emit(QuerySearchResults(query, search(query)))
        }
    }
}

internal data class QuerySearchResults<T>(
    val query: String,
    val items: List<T>,
)

internal fun <T> QuerySearchResults<T>.itemsFor(query: String): List<T> =
    items.takeIf { this.query == query }.orEmpty()
