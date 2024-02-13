package com.m3u.features.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Default
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.data.repository.PairState
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.TvRepository
import com.m3u.features.foryou.components.recommend.Recommend
import com.m3u.features.foryou.model.PlaylistDetail
import com.m3u.features.foryou.model.toDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    private val tvRepository: TvRepository,
    pref: Pref,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(Default) defaultDispatcher: CoroutineDispatcher
) : ViewModel() {
    internal val pinCodeForServer: StateFlow<String?> = tvRepository
        .pinCodeForServer
        .map { code ->
            code?.let { convertToPaddedString(it) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

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
            withContext(defaultDispatcher) {
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

    private val pinCodeForClient = MutableSharedFlow<Int?>()

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val pairStateForClient: StateFlow<PairState> =
        pinCodeForClient.flatMapLatest { pinCode ->
            if (pinCode != null) tvRepository.pairForClient(pinCode)
            else flow { }
        }
            .flowOn(ioDispatcher)
            .stateIn(
                scope = viewModelScope,
                initialValue = PairState.Idle,
                started = SharingStarted.WhileSubscribed(5_000)
            )

    internal fun pair(pin: Int) {
        viewModelScope.launch {
            pinCodeForClient.emit(pin)
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