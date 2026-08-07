package com.m3u.smartphone.ui.material.components

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelDetails
import com.m3u.data.repository.channel.ChannelDetailsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface ChannelDetailsState {
    /** Nothing to show and nothing on its way — live channels, mostly. */
    data object Absent : ChannelDetailsState

    data object Loading : ChannelDetailsState

    data class Content(val details: ChannelDetails) : ChannelDetailsState
}

/**
 * Holds the description shown when a channel sheet opens.
 *
 * Cached values appear immediately; anything missing is fetched once, in the
 * background, and lands through the database rather than being pushed here —
 * so a sheet closed mid-request still keeps what it paid for.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelDetailsViewModel @Inject constructor(
    private val repository: ChannelDetailsRepository,
) : ViewModel() {
    private val channel = MutableStateFlow<Channel?>(null)
    private var fetching: Job? = null

    val state: StateFlow<ChannelDetailsState> = channel
        .flatMapLatest { current ->
            if (current == null) flowOf(null) else repository.observe(current)
        }
        .map { details ->
            when {
                details == null -> ChannelDetailsState.Loading
                details.isEmpty -> ChannelDetailsState.Absent
                else -> ChannelDetailsState.Content(details)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChannelDetailsState.Loading)

    fun load(channel: Channel?) {
        if (this.channel.value?.id == channel?.id) return
        this.channel.value = channel
        fetching?.cancel()
        channel ?: return
        fetching = viewModelScope.launch { repository.fetchIfMissing(channel) }
    }
}

/**
 * A row the panel answered for but had nothing to say about. Kept in the
 * database so it is not asked again, shown as nothing at all.
 */
private val ChannelDetails.isEmpty: Boolean
    get() = plot.isNullOrBlank() &&
        cast.isNullOrBlank() &&
        director.isNullOrBlank() &&
        genre.isNullOrBlank() &&
        rating.isNullOrBlank() &&
        releaseDate.isNullOrBlank()
