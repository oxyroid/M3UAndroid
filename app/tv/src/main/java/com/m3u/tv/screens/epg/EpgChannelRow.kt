package com.m3u.tv.screens.epg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Programme
import com.m3u.tv.theme.JetStreamCardShape

@Composable
fun EpgChannelRow(
    channelData: ChannelProgrammeData,
    currentTime: Long,
    dpPerMinute: Float,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    // Clickable surface - entire row plays the channel when clicked
    Surface(
        onClick = { onChannelClick(channelData.channel) },
        modifier = modifier.height(120.dp),
        shape = ClickableSurfaceDefaults.shape(JetStreamCardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            pressedContainerColor = MaterialTheme.colorScheme.surface
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                ),
                shape = JetStreamCardShape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Channel info (left side) - title only
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
            }

            // Programme timeline (right side) - fills available width dynamically
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (channelData.currentProgramme != null || channelData.upcomingProgrammes.isNotEmpty()) {
                    // Combine current and upcoming programmes
                    val allProgrammes = listOfNotNull(channelData.currentProgramme) + channelData.upcomingProgrammes

                    allProgrammes.forEach { programme ->
                        // Calculate width based on duration (in minutes) using dynamic dpPerMinute
                        val durationMinutes = (programme.end - programme.start) / 60000f
                        val widthDp = (durationMinutes * dpPerMinute).dp
                        val isLive = programme == channelData.currentProgramme

                        EpgProgrammeCard(
                            programme = programme,
                            isLive = isLive,
                            currentTime = currentTime,
                            modifier = Modifier.width(widthDp.coerceAtLeast(100.dp)) // Minimum 100dp for readability
                        )
                    }
                } else {
                    // No programme data available
                    Surface(
                        onClick = { },
                        modifier = Modifier.fillMaxSize(),
                        shape = ClickableSurfaceDefaults.shape(JetStreamCardShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                text = "No programme data",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Data class to hold channel with its programmes
data class ChannelProgrammeData(
    val channel: Channel,
    val currentProgramme: Programme?,
    val upcomingProgrammes: List<Programme>
)