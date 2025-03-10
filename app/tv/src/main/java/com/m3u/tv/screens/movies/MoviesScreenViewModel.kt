package com.m3u.tv.screens.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.channel.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ChannelsScreenViewModel @Inject constructor(
    channelRepository: ChannelRepository
) : ViewModel() {
//    val uiState = combine(
//        channelRepository.getChannelsWithLongThumbnail(),
//        channelRepository.getPopularFilmsThisWeek(),
//    ) { (channels, popularFilmsThisWeek) ->
//        ChannelsScreenUiState.Ready(
//            channels = channels,
//            popularFilmsThisWeek = popularFilmsThisWeek
//        )
//    }.stateIn(
//        scope = viewModelScope,
//        started = SharingStarted.WhileSubscribed(5_000),
//        initialValue = ChannelsScreenUiState.Loading
//    )
}

sealed interface ChannelsScreenUiState {
    data object Loading : ChannelsScreenUiState
    data class Ready(
        val channels: List<Channel>,
        val popularFilmsThisWeek: List<Channel>
    ) : ChannelsScreenUiState
}
