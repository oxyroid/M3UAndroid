package com.m3u.tv.screens.epg

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.m3u.tv.theme.JetStreamCardShape
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun EpgHeader(
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

        // Refresh button only
        Button(
            onClick = onRefresh,
            enabled = !isRefreshing,
            shape = ButtonDefaults.shape(JetStreamCardShape)
        ) {
            Text(if (isRefreshing) "Refreshing..." else "🔄 Refresh")
        }
    }
}