package com.m3u.tv.screens.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository,
    private val programmeRepository: ProgrammeRepository
) : ViewModel() {

    // Track selected time offset (for time navigation)
    private val timeOffsetHours = MutableStateFlow(0)

    // Calculate current time based on offset
    val currentTime: StateFlow<Long> = timeOffsetHours
        .map { offset ->
            Clock.System.now().toEpochMilliseconds() + (offset * 3600000L)
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = Clock.System.now().toEpochMilliseconds(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Get all playlists to extract EPG URLs
    private val allPlaylists: StateFlow<List<Playlist>> = playlistRepository
        .observeAll()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Get all EPG URLs from playlists
    val epgUrls: StateFlow<List<String>> = allPlaylists
        .map { playlists ->
            playlists.flatMap { it.epgUrls }.distinct()
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Get only favorite channels that have EPG support (relationId != null)
    val favoriteChannelsWithEpg: StateFlow<List<Channel>> = channelRepository
        .observeAllFavorite()
        .map { channels ->
            channels.filter { it.relationId != null }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Track refreshing state
    val isRefreshing: StateFlow<Boolean> = programmeRepository.refreshingEpgUrls
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            initialValue = false,
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Get current programme for a channel
    suspend fun getCurrentProgramme(channelId: Int): Programme? {
        return programmeRepository.getProgrammeCurrently(channelId)
    }

    // Get programmes for a channel in a time range
    suspend fun getProgrammesForChannel(
        channel: Channel,
        startTime: Long,
        hours: Int = 6
    ): List<Programme> {
        val relationId = channel.relationId ?: return emptyList()
        val endTime = startTime + (hours * 3600000L)

        val playlist = allPlaylists.value.find { it.url == channel.playlistUrl }
        val epgUrls = playlist?.epgUrls ?: emptyList()

        if (epgUrls.isEmpty()) return emptyList()

        return programmeRepository.getProgrammesInTimeRange(
            epgUrls = epgUrls,
            relationId = relationId,
            startTime = startTime,
            endTime = endTime,
            limit = 10
        )
    }

    // Navigate to different time
    fun navigateToTime(offsetHours: Int) {
        timeOffsetHours.value = offsetHours
    }

    // Reset to current time
    fun navigateToNow() {
        timeOffsetHours.value = 0
    }

    // Refresh EPG data
    fun refreshEpg() {
        viewModelScope.launch {
            val urls = epgUrls.value
            if (urls.isNotEmpty()) {
                try {
                    programmeRepository.checkOrRefreshProgrammesOrThrow(
                        *urls.toTypedArray(),
                        ignoreCache = true
                    ).collect { /* Progress tracking if needed */ }
                } catch (e: Exception) {
                    // Handle error - could emit to an error state
                }
            }
        }
    }

    // Toggle favorite status
    fun toggleFavorite(channelId: Int) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(channelId)
        }
    }
}

// Data class to represent channel with its current and upcoming programmes
data class ChannelWithProgrammes(
    val channel: Channel,
    val currentProgramme: Programme?,
    val upcomingProgrammes: List<Programme>
)
