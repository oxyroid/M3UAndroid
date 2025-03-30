package com.m3u.tv.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.channel.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelRepository: ChannelRepository
) : ViewModel() {
    val channel: StateFlow<Channel?> = savedStateHandle
        .getStateFlow(ChannelScreen.ChannelIdBundleKey, -1)
        .flatMapLatest { id -> channelRepository.observe(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun updateFavorite() {
        val channel = channel.value ?: return
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(channel.id)
        }
    }
}
