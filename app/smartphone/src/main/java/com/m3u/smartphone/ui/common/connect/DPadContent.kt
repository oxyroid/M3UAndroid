package com.m3u.smartphone.ui.common.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.data.tv.model.TvInfo
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.FontFamilies
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun DPadContent(
    tvInfo: TvInfo,
    onRemoteDirection: (RemoteDirection) -> Unit,
    forgetTvCodeOnSmartphone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
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
            text = " ",
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
            Text(stringResource(string.ui_remote_control_disconnect).uppercase())
        }
    }
}
