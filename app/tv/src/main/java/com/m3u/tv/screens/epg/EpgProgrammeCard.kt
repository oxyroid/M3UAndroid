package com.m3u.tv.screens.epg

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.data.database.model.Programme
import com.m3u.tv.theme.JetStreamCardShape
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun EpgProgrammeCard(
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

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                color = if (isLive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = JetStreamCardShape
            )
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
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
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