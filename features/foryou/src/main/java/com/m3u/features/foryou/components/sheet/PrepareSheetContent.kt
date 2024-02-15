package com.m3u.features.foryou.components.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.core.util.basic.title
import com.m3u.features.foryou.components.CodeRow
import com.m3u.features.foryou.components.VirtualNumberKeyboard
import com.m3u.i18n.R

@Composable
@InternalComposeApi
internal fun ColumnScope.PrepareSheetContent(
    code: String,
    connecting: Boolean,
    onConnect: () -> Unit,
    onCode: (String) -> Unit
) {
    val title = stringResource(R.string.feat_foryou_connect_title).title()
    val subtitle = stringResource(R.string.feat_foryou_connect_subtitle)
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

        Text(
            text = subtitle,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.78f),
            modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)
        )
        CodeRow(
            code = code,
            length = 6,
            onClick = {}
        )

        TextButton(
            enabled = !connecting && code.length == 6,
            onClick = onConnect,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                when {
                    connecting -> "CONNECTING"
                    else -> "CONNECT"
                }
            )
        }
    }

    AnimatedVisibility(
        visible = !connecting,
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