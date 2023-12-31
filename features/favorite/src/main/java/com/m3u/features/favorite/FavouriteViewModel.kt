package com.m3u.features.favorite

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.observeAsFlow
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.Message
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.observeAll
import com.m3u.data.service.PlayerManager
import com.m3u.ui.Sort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class FavouriteViewModel @Inject constructor(
    streamRepository: StreamRepository,
    pref: Pref,
    playerManager: PlayerManager
) : BaseViewModel<FavoriteState, FavoriteEvent, Message.Static>(
    emptyState = FavoriteState()
) {
    private val zappingMode = pref
        .observeAsFlow { it.zappingMode }
        .stateIn(
            scope = viewModelScope,
            initialValue = Pref.DEFAULT_ZAPPING_MODE,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    val zapping = combine(
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

    val sorts: ImmutableList<Sort> = Sort.entries.toPersistentList()

    private val sortIndex = MutableStateFlow(0)

    val sort = sortIndex
        .map { sorts[it] }
        .stateIn(
            scope = viewModelScope,
            initialValue = Sort.UNSPECIFIED,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun sort(sort: Sort) {
        sortIndex.update { sorts.indexOf(sort).coerceAtLeast(0) }
    }

    val streams = streamRepository
        .observeAll { it.favourite }
        .combine(sort) { all, sort ->
            when (sort) {
                Sort.UNSPECIFIED -> all
                Sort.ASC -> all.sortedBy { it.title }
                Sort.DESC -> all.sortedByDescending { it.title }
            }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

}
