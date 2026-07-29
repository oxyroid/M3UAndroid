package com.m3u.smartphone.ui.common.connect

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.data.tv.model.TvInfo
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun DPadContent(
    tvInfo: TvInfo,
    onRemoteDirection: (RemoteDirection) -> Unit,
    forgetTvCodeOnSmartphone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val bidiFormatter = rememberUiBidiFormatter()
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text(
            text = bidiFormatter.natural(tvInfo.model),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(
                textDirection = TextDirection.ContentOrLtr,
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .semantics { heading() },
        )

        RemoteDirectionController(
            onRemoteDirection = onRemoteDirection
        )

        TextButton(
            onClick = forgetTvCodeOnSmartphone,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(top = 4.dp)
        ) {
            Text(stringResource(string.ui_remote_control_disconnect))
        }
    }
}
