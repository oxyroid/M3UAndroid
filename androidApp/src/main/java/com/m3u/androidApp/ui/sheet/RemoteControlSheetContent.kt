package com.m3u.androidApp.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.core.wrapper.Message
import com.m3u.data.television.model.RemoteDirection
import com.m3u.data.television.model.TelevisionInfo
import com.m3u.material.model.LocalSpacing

@Composable
@InternalComposeApi
internal fun ColumnScope.RemoteControlSheetContent(
    television: TelevisionInfo,
    message: Message,
    onRemoteDirection: (RemoteDirection) -> Unit,
    onDisconnect: () -> Unit
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text(
            text = television.model,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        AnimatedVisibility(
            visible = message.level != Message.LEVEL_EMPTY
        ) {
            Text(
                text = message.formatText(),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.78f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 0.dp, 16.dp, 8.dp)
            )
        }

        RemoteDirectionController(
            onRemoteDirection = onRemoteDirection
        )

        TextButton(
            onClick = onDisconnect,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("DISCONNECT")
        }
    }
}
