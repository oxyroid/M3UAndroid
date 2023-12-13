package com.m3u.features.favorite

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.util.collections.filterNotNullKeys
import com.m3u.core.wrapper.EmptyMessage
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
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
    liveRepository: LiveRepository,
    feedRepository: FeedRepository,
    pref: Pref,
    playerManager: PlayerManager
) : BaseViewModel<FavoriteState, FavoriteEvent, EmptyMessage>(
    emptyState = FavoriteState()
) {
    init {
        liveRepository
            .observeAll()
            .map { lives ->
                lives.filter { it.favourite }
            }
            .onEach { lives ->
                writable.update { state ->
                    state.copy(
                        details = lives
                            .groupBy { it.feedUrl }
                            .mapKeys { feedRepository.get(it.key)?.title }
                            .filterNotNullKeys()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private val zappingMode = pref
        .observeAsFlow { it.zappingMode }
        .onEach { Log.e("FAV", "$it") }
        .stateIn(
            scope = viewModelScope,
            initialValue = Pref.DEFAULT_ZAPPING_MODE,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val floating = combine(
        zappingMode,
        playerManager.url,
        liveRepository.observeAll()
    ) { zappingMode, url, lives ->
        if (!zappingMode) null
        else lives.find { it.url == url }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    override fun onEvent(event: FavoriteEvent) {

    }
}
