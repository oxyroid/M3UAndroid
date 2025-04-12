package com.m3u.smartphone.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.channel.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class SmartphoneViewModel @Inject constructor(
    private val channelRepository: ChannelRepository
): ViewModel() {
    val query = MutableStateFlow("")
    val channels: Flow<PagingData<Channel>> = query.flatMapLatest {
        Pager(PagingConfig(15)) {
            channelRepository.pagingAll(it)
        }
            .flow
            .cachedIn(viewModelScope)
    }
}