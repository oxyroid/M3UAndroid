package com.m3u.features.foryou.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.core.util.basic.title
import com.m3u.data.television.http.endpoint.SayHello
import com.m3u.i18n.R

@Composable
internal fun ConnectBottomSheet(
    visible: Boolean,
    connecting: Boolean,
    connectedTelevision: SayHello.Rep?,
    code: String,
    sheetState: SheetState,
    onCode: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                if (!connecting) onDismissRequest()
            },
            windowInsets = WindowInsets(0),
            properties = ModalBottomSheetDefaults.properties(
                shouldDismissOnBackPress = false
            )
        ) {
            Column(
                modifier.padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues())
            ) {
                val title = when {
                    connectedTelevision != null -> connectedTelevision.model
                    else -> stringResource(R.string.feat_foryou_connect_title).title()
                }
                val subtitle = stringResource(R.string.feat_foryou_connect_subtitle)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (connectedTelevision == null) {
                        Text(
                            text = subtitle,
                            modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.78f)
                        )
                        CodeRow(
                            code = code,
                            length = 6,
                            onClick = {}
                        )
                    }
                    TextButton(
                        enabled = connectedTelevision != null || (!connecting && code.length == 6),
                        onClick = when {
                            connectedTelevision != null -> onDisconnect
                            else -> onConnect
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            when {
                                connectedTelevision != null -> "DISCONNECT"
                                connecting -> "CONNECTING"
                                else -> "CONNECT"
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = connectedTelevision == null,
                    enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut()
                ) {
                    VirtualNumberKeyboard(
                        code = code,
                        onCode = onCode,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
