package com.m3u.tv.screens.shows

import androidx.lifecycle.ViewModel
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.channel.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShowScreenViewModel @Inject constructor(
    channelRepository: ChannelRepository
) : ViewModel() {

//    val uiState = combine(
//        channelRepository.getBingeWatchDramas(),
//        channelRepository.getTVShows()
//    ) { (bingeWatchDramaList, tvShowList) ->
//        ShowScreenUiState.Ready(bingeWatchDramaList = bingeWatchDramaList, tvShowList = tvShowList)
//    }.stateIn(
//        scope = viewModelScope,
//        started = SharingStarted.WhileSubscribed(5_000),
//        initialValue = ShowScreenUiState.Loading
//    )
}

sealed interface ShowScreenUiState {
    data object Loading : ShowScreenUiState
    data class Ready(
        val bingeWatchDramaList: List<Channel>,
        val tvShowList: List<Channel>
    ) : ShowScreenUiState
}
