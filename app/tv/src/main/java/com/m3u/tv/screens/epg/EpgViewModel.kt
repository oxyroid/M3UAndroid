package com.m3u.tv.screens.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.database.model.type
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository,
    private val programmeRepository: ProgrammeRepository
) : ViewModel() {

    // Track selected time offset (for time navigation)
    private val timeOffsetHours = MutableStateFlow(0)

    // Timeline start time - starts 1 hour before current time, only updates when time offset changes
    val currentTime: StateFlow<Long> = timeOffsetHours
        .map { offset ->
            // Start timeline 1 hour before current time, so red line appears in the middle
            val now = Clock.System.now().toEpochMilliseconds()
            now - 3600000L + (offset * 3600000L)  // 3600000L = 1 hour in milliseconds
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = Clock.System.now().toEpochMilliseconds() - 3600000L,  // Start 1 hour ago
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Live "NOW" time for red line - updates every 5 seconds for smooth movement
    // Enterprise-level: Red line moves smoothly across channels showing real-time
    val liveCurrentTime: StateFlow<Long> = flow {
        while (true) {
            emit(Clock.System.now().toEpochMilliseconds())
            delay(5.seconds) // Smooth 5-second updates for red line only
        }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = Clock.System.now().toEpochMilliseconds(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Enterprise-level: Auto-refresh EPG data every 2 minutes with proper error handling and cancellation
    private var autoRefreshJob: Job? = null

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            delay(10.seconds) // Initial delay to let the screen load
            while (isActive) {  // Check for cancellation
                try {
                    delay(2.minutes)
                    android.util.Log.d("EpgViewModel", "🔄 Auto-refresh: Refreshing EPG data every 2 minutes")
                    refreshEpg()
                } catch (e: CancellationException) {
                    android.util.Log.d("EpgViewModel", "Auto-refresh cancelled")
                    throw e  // Re-throw cancellation
                } catch (e: Exception) {
                    android.util.Log.e("EpgViewModel", "✗ Auto-refresh failed: ${e.message}", e)
                    delay(5.minutes)  // Longer delay on failure to avoid excessive retries
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        android.util.Log.d("EpgViewModel", "Auto-refresh stopped: ViewModel cleared")
    }

    // Get all playlists to extract EPG URLs
    private val allPlaylists: StateFlow<List<Playlist>> = playlistRepository
        .observeAll()
        .map { playlists ->
            android.util.Log.d("EpgViewModel", "=== ALL PLAYLISTS DEBUG ===")
            android.util.Log.d("EpgViewModel", "Total playlists in database: ${playlists.size}")
            playlists.forEach { p ->
                android.util.Log.d("EpgViewModel", "  - Playlist: ${p.title}")
                android.util.Log.d("EpgViewModel", "    source = ${p.source} (value='${p.source.value}')")
                android.util.Log.d("EpgViewModel", "    url = ${p.url.take(60)}...")
                android.util.Log.d("EpgViewModel", "    Is EPG? ${p.source == DataSource.EPG}")
            }
            val epgPlaylists = playlists.filter { it.source == DataSource.EPG }
            android.util.Log.d("EpgViewModel", "")
            android.util.Log.d("EpgViewModel", "EPG playlists found: ${epgPlaylists.size}")
            if (epgPlaylists.isNotEmpty()) {
                epgPlaylists.forEach { p ->
                    android.util.Log.d("EpgViewModel", "  - EPG Playlist: ${p.title}, url=${p.url}")
                }
            } else {
                android.util.Log.w("EpgViewModel", "⚠️ NO EPG PLAYLISTS FOUND IN DATABASE!")
                android.util.Log.w("EpgViewModel", "⚠️ Expected to find at least one playlist with source=DataSource.EPG")
            }
            android.util.Log.d("EpgViewModel", "=== END DEBUG ===")
            playlists
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Get all EPG URLs from playlists (including auto-generated Xtream URLs and EPG playlists)
    val epgUrls: StateFlow<List<String>> = allPlaylists
        .map { playlists ->
            android.util.Log.d("EpgViewModel", "=== EPG URL MAPPING START ===")
            android.util.Log.d("EpgViewModel", "Total playlists loaded: ${playlists.size}")

            playlists.forEach { playlist ->
                android.util.Log.d("EpgViewModel", "Playlist: ${playlist.title}")
                android.util.Log.d("EpgViewModel", "  - source: ${playlist.source}")
                android.util.Log.d("EpgViewModel", "  - url: ${playlist.url.take(60)}...")

                when (playlist.source) {
                    DataSource.EPG -> {
                        android.util.Log.d("EpgViewModel", "  - EPG playlist detected! Will use url: ${playlist.url}")
                    }
                    else -> {
                        val urls = playlist.epgUrlsOrXtreamXmlUrl()
                        android.util.Log.d("EpgViewModel", "  - Non-EPG playlist, epgUrls: $urls")
                    }
                }
            }

            // Get EPG URLs from:
            // 1. URL from EPG-type playlists (the playlist URL itself is the EPG URL)
            // 2. epgUrls field from M3U/Xtream playlists
            val allUrls = playlists.flatMap {
                when (it.source) {
                    DataSource.EPG -> {
                        android.util.Log.d("EpgViewModel", "Adding EPG playlist URL: ${it.url}")
                        listOf(it.url)  // For EPG playlists, the URL IS the EPG URL
                    }
                    else -> {
                        val urls = it.epgUrlsOrXtreamXmlUrl()
                        if (urls.isNotEmpty()) {
                            android.util.Log.d("EpgViewModel", "Adding M3U/Xtream EPG URLs: $urls")
                        }
                        urls  // For M3U/Xtream, get from epgUrls field
                    }
                }
            }.distinct()

            android.util.Log.d("EpgViewModel", "=== FINAL EPG URLs: $allUrls ===")
            allUrls
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000)
        )

    // Get only individually favorited channels that have EPG support (relationId != null)
    val favoriteChannelsWithEpg: StateFlow<List<Channel>> = channelRepository
        .observeAllFavorite()  // Only get channels marked as favorite (green)
        .map { favoriteChannels ->
            android.util.Log.d("EpgViewModel", "=== EPG Channel Selection (Favorite Channels Only) ===")
            android.util.Log.d("EpgViewModel", "Total favorite channels: ${favoriteChannels.size}")

            // Filter to only channels with EPG support (have tvg-id)
            val channelsWithRelationId = favoriteChannels.filter { it.relationId != null }
            android.util.Log.d("EpgViewModel", "Favorite channels with EPG support (relationId): ${channelsWithRelationId.size}")

            if (channelsWithRelationId.isEmpty() && favoriteChannels.isNotEmpty()) {
                android.util.Log.w("EpgViewModel", "⚠️ WARNING: ${favoriteChannels.size} favorite channels found but NONE have relationId!")
                android.util.Log.w("EpgViewModel", "⚠️ Channels need 'tvg-id' attribute in M3U file to match EPG data")
                android.util.Log.w("EpgViewModel", "⚠️ Mark channels as favorite (green star) in playlist management to show them in EPG")
                favoriteChannels.take(5).forEach { ch ->
                    android.util.Log.w("EpgViewModel", "  - Favorite channel without tvg-id: ${ch.title}")
                }
            } else if (channelsWithRelationId.isNotEmpty()) {
                android.util.Log.d("EpgViewModel", "Showing EPG for these favorite channels:")
                channelsWithRelationId.take(10).forEach { ch ->
                    android.util.Log.d("EpgViewModel", "  - ${ch.title} (relationId: ${ch.relationId})")
                }
            }

            android.util.Log.d("EpgViewModel", "=== End Diagnostic ===")

            channelsWithRelationId
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
        val relationId = channel.relationId ?: run {
            android.util.Log.w("EpgViewModel", "Channel '${channel.title}' has no relationId")
            return emptyList()
        }
        val endTime = startTime + (hours * 3600000L)

        // SIMPLE: Use the manual EPG URL directly
        val epgUrl = _manualEpgUrl.value
        if (epgUrl == null) {
            android.util.Log.w("EpgViewModel", "No manual EPG URL configured!")
            return emptyList()
        }

        val urls = listOf(epgUrl)
        android.util.Log.d("EpgViewModel", "Fetching programmes for '${channel.title}' (relationId: $relationId)")
        android.util.Log.d("EpgViewModel", "EPG URL: $epgUrl")
        android.util.Log.d("EpgViewModel", "Time range: $startTime to $endTime")

        // Try original relationId first
        var programmes = programmeRepository.getProgrammesInTimeRange(
            epgUrls = urls,
            relationId = relationId,
            startTime = startTime,
            endTime = endTime,
            limit = 10
        )
        android.util.Log.d("EpgViewModel", "Query with original ID '$relationId' returned ${programmes.size} programmes")

        // If no programmes found, try normalized channel ID
        if (programmes.isEmpty()) {
            val normalizedId = normalizeChannelId(relationId)
            if (normalizedId != relationId) {
                android.util.Log.d("EpgViewModel", "Trying normalized ID: $normalizedId (from $relationId)")
                programmes = programmeRepository.getProgrammesInTimeRange(
                    epgUrls = urls,
                    relationId = normalizedId,
                    startTime = startTime,
                    endTime = endTime,
                    limit = 10
                )
                android.util.Log.d("EpgViewModel", "Query with normalized ID '$normalizedId' returned ${programmes.size} programmes")
            }
        }

        android.util.Log.d("EpgViewModel", "✓ Found ${programmes.size} programmes for '${channel.title}'")
        if (programmes.isEmpty()) {
            android.util.Log.w("EpgViewModel", "⚠️ No programmes found for '${channel.title}' with relationId '$relationId'")
        }

        return programmes
    }

    // Normalize channel ID to match EPG format
    // Example: "SVT1 HD (T).se" -> "svt1.se"
    private fun normalizeChannelId(relationId: String): String {
        return relationId
            .lowercase() // SVT1 -> svt1
            .replace(Regex("\\s+hd.*?(?=\\.|$)"), "") // Remove " HD", " HD (T)", etc.
            .replace(Regex("\\s+fhd.*?(?=\\.|$)"), "") // Remove " FHD"
            .replace(Regex("\\s*\\([^)]*\\)"), "") // Remove (T), (S), etc.
            .replace(Regex("\\s+sweden"), "") // Remove " Sweden"
            .replace(Regex("\\s+danmark"), "-danmark") // Keep country suffix
            .replace(Regex("\\s+norge"), "-norge") // Keep country suffix
            .replace(Regex("\\s+"), "-") // Replace spaces with hyphens
            .trim()
    }

    // Navigate to different time
    fun navigateToTime(offsetHours: Int) {
        timeOffsetHours.value = offsetHours
    }

    // Reset to current time
    fun navigateToNow() {
        timeOffsetHours.value = 0
    }

    // Simple EPG URL storage - bypass the playlist architecture completely
    private val _manualEpgUrl = MutableStateFlow<String?>("https://raw.githubusercontent.com/globetvapp/epg/refs/heads/main/Sweden/sweden1.xml")

    // Refresh EPG data
    fun refreshEpg() {
        viewModelScope.launch {
            val epgUrl = _manualEpgUrl.value ?: return@launch

            android.util.Log.d("EpgViewModel", "=== SIMPLE EPG REFRESH START ===")
            android.util.Log.d("EpgViewModel", "EPG URL: $epgUrl")

            try {
                // Create EPG playlist in database for the download worker
                playlistRepository.insertEpgAsPlaylist(
                    title = "Sweden EPG (Auto)",
                    epg = epgUrl
                )
                android.util.Log.d("EpgViewModel", "✓ EPG playlist created/updated")

                // Trigger download
                android.util.Log.d("EpgViewModel", "Starting EPG download...")
                var programmeCount = 0
                programmeRepository.checkOrRefreshProgrammesOrThrow(
                    epgUrl,
                    ignoreCache = true
                ).collect { count ->
                    programmeCount = count
                    if (count % 100 == 0) {
                        android.util.Log.d("EpgViewModel", "EPG refresh progress: $count programmes")
                    }
                }
                android.util.Log.d("EpgViewModel", "✓ EPG refresh completed - $programmeCount programmes inserted")
                android.util.Log.d("EpgViewModel", "=== EPG REFRESH SUCCESS ===")
            } catch (e: Exception) {
                android.util.Log.e("EpgViewModel", "✗ EPG refresh failed: ${e.message}", e)
                e.printStackTrace()
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
