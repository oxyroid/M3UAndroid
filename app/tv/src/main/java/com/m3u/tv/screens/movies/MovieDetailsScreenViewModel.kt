package com.m3u.tv.screens.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.tv.screens.movies.ChannelScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ChannelScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ChannelRepository,
) : ViewModel() {
    val uiState = savedStateHandle
        .getStateFlow<String?>(ChannelScreen.ChannelIdBundleKey, null)
        .map { id ->
            if (id == null) {
                ChannelScreenUiState.Error
            } else {
                val channel = repository.get(id = id.toIntOrNull() ?: 0)
                channel?.let { ChannelScreenUiState.Done(channel = it) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChannelScreenUiState.Loading
        )
}

sealed class ChannelScreenUiState {
    data object Loading : ChannelScreenUiState()
    data object Error : ChannelScreenUiState()
    data class Done(val channel: Channel) : ChannelScreenUiState()
}
