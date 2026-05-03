package com.m3u.smartphone.ui.common.connect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string

@Composable
@InternalComposeApi
internal fun PrepareContent(
    code: String,
    searchingOrConnecting: Boolean,
    checkTvCodeOnSmartphone: () -> Unit,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = stringResource(string.ui_remote_control_pair_subtitle)
) {
    val title = stringResource(string.ui_remote_control_pair_title).title()
    Column(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )

            AnimatedContent(
                targetState = subtitle,
                label = "remote-control-subtitle",
                transitionSpec = {
                    fadeIn() + slideInVertically { it } togetherWith fadeOut() + slideOutVertically { it }
                }
            ) { currentSubtitle ->
                Text(
                    text = currentSubtitle,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.78f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 8.dp, 16.dp, 0.dp)
                )
            }
            CodeRow(
                code = code,
                length = 6,
                onClick = {}
            )

            Button(
                enabled = !searchingOrConnecting && code.length == 6,
                onClick = checkTvCodeOnSmartphone,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .widthIn(min = 120.dp)
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    when {
                        searchingOrConnecting -> stringResource(string.ui_remote_control_connecting).uppercase()
                        else -> stringResource(string.ui_remote_control_connect).uppercase()
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = !searchingOrConnecting,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut()
        ) {
            val hapticFeedback = LocalHapticFeedback.current
            VirtualNumberKeyboard(
                code = code,
                onCode = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCode(it)
                },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
