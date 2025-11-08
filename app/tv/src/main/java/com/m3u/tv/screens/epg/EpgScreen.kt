package com.m3u.tv.screens.epg

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Programme
import com.m3u.tv.theme.JetStreamBottomListPadding
import com.m3u.tv.theme.JetStreamBorderWidth
import com.m3u.tv.theme.JetStreamCardShape
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

@Composable
fun EpgScreen(
    modifier: Modifier = Modifier,
    viewModel: EpgViewModel = hiltViewModel()
) {
    val channels by viewModel.favoriteChannelsWithEpg.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
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
                    hours = 6
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
            currentTime = currentTime,
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

        // Channel list with EPG timeline
        if (channelsWithProgrammes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (channels.isEmpty()) {
                        "No favorite channels with EPG data.\n\nAdd channels to favorites to see their TV guide here."
                    } else {
                        "Loading EPG data..."
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgHeader(
    currentTime: Long,
    isRefreshing: Boolean,
    onNavigateToNow: () -> Unit,
    onNavigateToTime: (Int) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val instant = Instant.fromEpochMilliseconds(currentTime)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val timeString = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    val dateString = "${dateTime.dayOfMonth}/${dateTime.monthNumber}/${dateTime.year}"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time display
        Column {
            Text(
                text = "📺 TV Guide",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$dateString • $timeString",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Time navigation buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onNavigateToNow,
                shape = ButtonDefaults.shape(JetStreamCardShape)
            ) {
                Text("Now")
            }
            Button(
                onClick = { onNavigateToTime(1) },
                shape = ButtonDefaults.shape(JetStreamCardShape)
            ) {
                Text("+1h")
            }
            Button(
                onClick = { onNavigateToTime(2) },
                shape = ButtonDefaults.shape(JetStreamCardShape)
            ) {
                Text("+2h")
            }
            Button(
                onClick = { onNavigateToTime(3) },
                shape = ButtonDefaults.shape(JetStreamCardShape)
            ) {
                Text("+3h")
            }
            Button(
                onClick = onRefresh,
                enabled = !isRefreshing,
                shape = ButtonDefaults.shape(JetStreamCardShape)
            ) {
                Text(if (isRefreshing) "Refreshing..." else "🔄 Refresh")
            }
        }
    }
}

@Composable
private fun EpgChannelRow(
    channelData: ChannelProgrammeData,
    currentTime: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(120.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = JetStreamCardShape
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Channel info (left side)
        Column(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = channelData.channel.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (channelData.channel.category.isNotBlank()) {
                Text(
                    text = channelData.channel.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Programme timeline (right side)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Current programme
            if (channelData.currentProgramme != null) {
                EpgProgrammeCard(
                    programme = channelData.currentProgramme,
                    isLive = true,
                    currentTime = currentTime,
                    modifier = Modifier.weight(1f)
                )
            }

            // Upcoming programmes (limited to 2-3 for space)
            channelData.upcomingProgrammes.take(2).forEach { programme ->
                EpgProgrammeCard(
                    programme = programme,
                    isLive = false,
                    currentTime = currentTime,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EpgProgrammeCard(
    programme: Programme,
    isLive: Boolean,
    currentTime: Long,
    modifier: Modifier = Modifier
) {
    val startTime = Instant.fromEpochMilliseconds(programme.start)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val endTime = Instant.fromEpochMilliseconds(programme.end)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val timeString = "${startTime.hour.toString().padStart(2, '0')}:${startTime.minute.toString().padStart(2, '0')}" +
            " - ${endTime.hour.toString().padStart(2, '0')}:${endTime.minute.toString().padStart(2, '0')}"

    // Calculate progress for live programmes
    val progress = if (isLive) {
        val total = (programme.end - programme.start).toFloat()
        val elapsed = (currentTime - programme.start).toFloat()
        (elapsed / total).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "progress"
    )

    Surface(
        onClick = { },
        modifier = modifier.fillMaxHeight(),
        shape = ClickableSurfaceDefaults.shape(JetStreamCardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isLive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (isLive) {
                Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = JetStreamBorderWidth,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    shape = JetStreamCardShape
                )
            } else {
                Border.None
            }
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Programme title and time
                Column {
                    if (isLive) {
                        Text(
                            text = "▶ LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = programme.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Progress bar for live programme
                if (isLive && animatedProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                shape = JetStreamCardShape
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = JetStreamCardShape
                                )
                        )
                    }
                }
            }
        }
    }
}

// Data class to hold channel with its programmes
private data class ChannelProgrammeData(
    val channel: Channel,
    val currentProgramme: Programme?,
    val upcomingProgrammes: List<Programme>
)
