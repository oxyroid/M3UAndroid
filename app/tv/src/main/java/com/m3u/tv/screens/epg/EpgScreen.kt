package com.m3u.tv.screens.epg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.m3u.data.database.model.Channel
import com.m3u.tv.theme.JetStreamBottomListPadding
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

@Composable
fun EpgScreen(
    modifier: Modifier = Modifier,
    viewModel: EpgViewModel = hiltViewModel(),
    onChannelClick: (Channel) -> Unit = { }  // Add navigation callback
) {
    val channels by viewModel.favoriteChannelsWithEpg.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val liveCurrentTime by viewModel.liveCurrentTime.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val scope = rememberCoroutineScope()

    // State to hold channels with their programmes
    var channelsWithProgrammes by remember { mutableStateOf<List<ChannelProgrammeData>>(emptyList()) }

    // Load programmes for all channels when they change or time changes
    LaunchedEffect(channels, currentTime) {
        if (channels.isNotEmpty()) {
            val data = channels.map { channel ->
                val programmes = viewModel.getProgrammesForChannel(
                    channel = channel,
                    startTime = currentTime,
                    hours = 4  // Show 4 hours ahead for better text readability
                )
                val current = viewModel.getCurrentProgramme(channel.id)
                ChannelProgrammeData(
                    channel = channel,
                    currentProgramme = current,
                    upcomingProgrammes = programmes
                )
            }
            channelsWithProgrammes = data
        } else {
            channelsWithProgrammes = emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header with time navigation
        EpgHeader(
            currentTime = liveCurrentTime,  // Use liveCurrentTime for live header updates
            isRefreshing = isRefreshing,
            onNavigateToNow = { viewModel.navigateToNow() },
            onNavigateToTime = { offsetHours -> viewModel.navigateToTime(offsetHours) },
            onRefresh = {
                scope.launch {
                    viewModel.refreshEpg()
                }
            },
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Use BoxWithConstraints to calculate dynamic timeline width
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Calculate available width for timeline:
            // Total width - horizontal padding (48dp × 2) - channel name column (180dp) - spacing (16dp)
            val availableTimelineWidth = maxWidth - 96.dp - 180.dp - 16.dp

            // Calculate dp per minute to fit exactly 4 hours (240 minutes) in available width
            // Timeline starts 1 hour before current time and shows 4 hours total
            // Use .value to convert Dp to Float for calculation
            val dpPerMinute = (availableTimelineWidth.value / 240f).coerceAtLeast(2f) // Minimum 2dp per minute for readability

            Column(modifier = Modifier.fillMaxSize()) {
                // Timeline ruler showing hours
                if (channelsWithProgrammes.isNotEmpty()) {
                    EpgTimelineRuler(
                        currentTime = currentTime,
                        hours = 4,
                        dpPerMinute = dpPerMinute,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Channel list with EPG timeline
                if (channelsWithProgrammes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(48.dp)
                        ) {
                            Text(
                                text = if (channels.isEmpty()) {
                                    "📺 No Channels Found"
                                } else {
                                    "⏳ Loading TV Guide..."
                                },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = when {
                                    channels.isEmpty() -> {
                                        "To see EPG data:\n\n" +
                                        "1. Go to Settings → Prenumerera → Manage Playlists\n" +
                                        "2. Select a playlist, then select a channel\n" +
                                        "3. Press SELECT button to mark as favorite (green star)\n" +
                                        "4. Ensure channels have 'tvg-id' attributes in M3U file\n" +
                                        "5. Click Refresh button above to download EPG data\n\n" +
                                        "Only individually favorited channels appear in EPG."
                                    }
                                    channelsWithProgrammes.isEmpty() && channels.isNotEmpty() -> {
                                        "Found ${channels.size} favorite channels but no programme data yet.\n\n" +
                                        "Possible reasons:\n" +
                                        "• EPG data is still downloading (click Refresh above)\n" +
                                        "• Channels lack 'tvg-id' attributes in M3U file\n" +
                                        "• EPG channel IDs don't match M3U tvg-id values\n\n" +
                                        "Check logs for detailed diagnostics."
                                    }
                                    else -> "Loading EPG data from database..."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    // Box to overlay the "NOW" line on top of the channel list
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 48.dp,
                                end = 48.dp,
                                bottom = JetStreamBottomListPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = channelsWithProgrammes,
                                key = { it.channel.id }
                            ) { data ->
                                EpgChannelRow(
                                    channelData = data,
                                    currentTime = currentTime,
                                    dpPerMinute = dpPerMinute,
                                    onChannelClick = onChannelClick,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Red vertical "NOW" line (enterprise-level feature)
                        // Shows current time across all channels - updates every 5 seconds
                        val minutesFromStart = (liveCurrentTime - currentTime) / 60000f
                        val nowLineOffset = minutesFromStart * dpPerMinute

                        // Only draw the line if it's within the visible 4-hour window
                        if (nowLineOffset >= 0f && nowLineOffset <= availableTimelineWidth.value) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .offset(x = (48.dp + 180.dp + 16.dp + nowLineOffset.dp))
                            ) {
                                drawRect(
                                    color = Color.Red,
                                    alpha = 0.8f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgTimelineRuler(
    currentTime: Long,
    hours: Int,
    dpPerMinute: Float,
    modifier: Modifier = Modifier
) {
    // Match the channel row layout: 180dp for channel name + timeline
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Spacer matching channel name width
        Spacer(modifier = Modifier.width(180.dp))

        // Timeline markers - show 30-minute intervals
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Show time markers every 30 minutes (2 markers per hour)
            val totalMarkers = (hours * 2) + 1  // 30-min intervals = 2 per hour, +1 for end marker
            repeat(totalMarkers) { markerIndex ->
                val minutesOffset = markerIndex * 30
                val time = currentTime + (minutesOffset * 60000L)  // 60000L = 1 minute in milliseconds
                val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(time)
                val dateTime = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                val timeString = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"

                // Width for 30 minutes = 30 * dpPerMinute (dynamically calculated)
                val markerWidth = (30f * dpPerMinute).dp

                Column(
                    modifier = Modifier
                        .width(markerWidth)
                        .padding(start = if (markerIndex == 0) 0.dp else 4.dp)
                ) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    ) {
                        drawRect(
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}
