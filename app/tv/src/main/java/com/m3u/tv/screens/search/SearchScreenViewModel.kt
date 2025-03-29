package com.m3u.tv.screens.search

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.m3u.data.database.model.Channel
import com.m3u.data.repository.channel.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class SearchScreenViewModel @Inject constructor(
    private val channelRepository: ChannelRepository
) : ViewModel() {
    val channels: Flow<PagingData<Channel>> = snapshotFlow { searchQuery.value }
        .flatMapLatest { query ->
            if (query.isBlank()) {
                emptyFlow()
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        prefetchDistance = 5
                    ),
                    pagingSourceFactory = { channelRepository.search(query) }
                )
                    .flow
            }
        }

    var searchQuery = mutableStateOf("")
}

