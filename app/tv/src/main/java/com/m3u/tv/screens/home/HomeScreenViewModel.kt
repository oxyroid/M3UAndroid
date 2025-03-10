package com.m3u.tv.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.channel.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeScreeViewModel @Inject constructor(channelRepository: ChannelRepository) : ViewModel() {
//    val uiState: StateFlow<HomeScreenUiState> = combine(
//        channelRepository.getFeaturedChannels(),
//        channelRepository.getTrendingChannels(),
//        channelRepository.getTop10Channels(),
//        channelRepository.getNowPlayingChannels(),
//    ) { featuredChannels, trendingChannels, top10Channels, nowPlayingChannels ->
//        HomeScreenUiState.Ready(
//            featuredChannels,
//            trendingChannels,
//            top10Channels,
//            nowPlayingChannels
//        )
//    }.stateIn(
//        scope = viewModelScope,
//        started = SharingStarted.WhileSubscribed(5_000),
//        initialValue = HomeScreenUiState.Loading
//    )
}

sealed interface HomeScreenUiState {
    data object Loading : HomeScreenUiState
    data object Error : HomeScreenUiState
    data class Ready(
        val featuredChannels: List<Channel>,
        val trendingChannels: List<Channel>,
        val top10Channels: List<Channel>,
        val nowPlayingChannels: List<Channel>
    ) : HomeScreenUiState
}
