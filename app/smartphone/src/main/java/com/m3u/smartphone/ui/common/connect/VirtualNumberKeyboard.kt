package com.m3u.smartphone.ui.common.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.i18n.R.string

@Composable
internal fun VirtualNumberKeyboard(
    modifier: Modifier = Modifier,
    code: String,
    onCode: (String) -> Unit,
) {
    val digitKeysEnabled = areRemoteDigitKeysEnabled(code.length)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.onSurface.copy(.1f))
        ) {
            Row(Modifier.fillMaxWidth()) {
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "1",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "1") }
                )
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "2",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "2") }
                )
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "3",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "3") }
                )
            }
            Row(Modifier.fillMaxWidth()) {
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "4",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "4") }
                )
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "5",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "5") }
                )
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "6",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "6") }
                )
            }
            Row(Modifier.fillMaxWidth()) {
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "7",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "7") }
                )
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "8",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "8") }
                )
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "9",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "9") }
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .heightIn(min = 54.dp)
                        .weight(1f)
                        .clickable(
                            enabled = code.isNotEmpty(),
                            onClick = {
                                onCode(code.substring(0, code.length - 1))
                            },
                            indication = ripple(color = MaterialTheme.colorScheme.primary),
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        modifier = Modifier.size(38.dp),
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = stringResource(string.ui_remote_control_keypad_backspace)
                    )
                }
                KeyboardKey(
                    modifier = Modifier.weight(1f),
                    text = "0",
                    enabled = digitKeysEnabled,
                    onClick = { onCode(code + "0") }
                )

                Column(
                    modifier = Modifier
                        .heightIn(min = 54.dp)
                        .weight(1f)
                        .clickable(
                            enabled = code.isNotEmpty(),
                            onClick = { onCode("") },
                            indication = ripple(color = MaterialTheme.colorScheme.primary),
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        modifier = Modifier.size(38.dp),
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(string.ui_remote_control_keypad_clear)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    modifier: Modifier,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier.heightIn(min = 54.dp),
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 0.8f else 0.38f
            )
        )
    }
}

internal fun areRemoteDigitKeysEnabled(codeLength: Int): Boolean = codeLength < 6
