package com.m3u.androidApp.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.m3u.data.service.collectMessageAsState
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.data.tv.model.TvInfo
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.FontFamilies

@Composable
@InternalComposeApi
internal fun DPadContent(
    tvInfo: TvInfo,
    onRemoteDirection: (RemoteDirection) -> Unit,
    forgetTvCodeOnSmartphone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val message by collectMessageAsState()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text(
            text = tvInfo.model,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        Text(
            text = message.formatText().ifEmpty { " " },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            fontFamily = FontFamilies.JetbrainsMono,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 0.dp, 16.dp, 8.dp)
        )

        RemoteDirectionController(
            onRemoteDirection = onRemoteDirection
        )

        TextButton(
            onClick = forgetTvCodeOnSmartphone,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("DISCONNECT")
        }
    }
}
