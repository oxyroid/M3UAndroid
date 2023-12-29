package com.m3u.features.favorite

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.util.collections.filterNotNullKeys
import com.m3u.core.wrapper.Message
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class FavouriteViewModel @Inject constructor(
    streamRepository: StreamRepository,
    playlistRepository: PlaylistRepository,
    pref: Pref,
    playerManager: PlayerManager
) : BaseViewModel<FavoriteState, FavoriteEvent, Message.Static>(
    emptyState = FavoriteState()
) {
    init {
        streamRepository
            .observeAll()
            .map { streams ->
                streams.filter { it.favourite }
            }
            .onEach { streams ->
                writable.update { state ->
                    state.copy(
                        details = streams
                            .groupBy { it.playlistUrl }
                            .mapKeys { playlistRepository.get(it.key)?.title }
                            .filterNotNullKeys()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private val zappingMode = pref
        .observeAsFlow { it.zappingMode }
        .stateIn(
            scope = viewModelScope,
            initialValue = Pref.DEFAULT_ZAPPING_MODE,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val floating = combine(
        zappingMode,
        playerManager.url,
        streamRepository.observeAll()
    ) { zappingMode, url, streams ->
        if (!zappingMode) null
        else streams.find { it.url == url }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    override fun onEvent(event: FavoriteEvent) {

    }
}
