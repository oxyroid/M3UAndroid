package com.m3u.androidApp.ui.sheet

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.androidApp.ui.CodeRow
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Message
import com.m3u.data.service.collectMessageAsState
import com.m3u.i18n.R

@Composable
@InternalComposeApi
internal fun PrepareContent(
    code: String,
    searchingOrConnecting: Boolean,
    checkTvCodeOnSmartphone: () -> Unit,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val message by collectMessageAsState()

    val title = stringResource(R.string.feat_foryou_connect_title).title()
    val subtitle = if (message.level == Message.LEVEL_EMPTY) {
        stringResource(R.string.feat_foryou_connect_subtitle)
    } else {
        message.formatText()
    }
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
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )

            AnimatedContent(
                targetState = subtitle,
                label = "subtitle",
                transitionSpec = {
                    fadeIn() + slideInVertically { it } togetherWith fadeOut() + slideOutVertically { it }
                },
            ) { subtitle ->
                Text(
                    text = subtitle,
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

            TextButton(
                enabled = !searchingOrConnecting && code.length == 6,
                onClick = checkTvCodeOnSmartphone,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    when {
                        searchingOrConnecting -> "CONNECTING"
                        else -> "CONNECT"
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