package com.m3u.androidApp.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ripple
import com.m3u.material.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VirtualNumberKeyboard(
    modifier: Modifier = Modifier,
    code: String,
    onCode: (String) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .background(MaterialTheme.colorScheme.onSurface.copy(.1f))
    ) {
        Row(
            Modifier.fillMaxWidth()
        ) {
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "1",
                onClick = { if (code.length < 6) onCode(code + "1") }
            )
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "2",
                onClick = { if (code.length < 6) onCode(code + "2") }
            )
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "3",
                onClick = { if (code.length < 6) onCode(code + "3") }
            )
        }
        Row(
            Modifier.fillMaxWidth()
        ) {
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "4",
                onClick = { if (code.length < 6) onCode(code + "4") }
            )
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "5",
                onClick = { if (code.length < 6) onCode(code + "5") }
            )
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "6",
                onClick = { if (code.length < 6) onCode(code + "6") }
            )
        }
        Row(
            Modifier.fillMaxWidth()
        ) {
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "7",
                onClick = { if (code.length < 6) onCode(code + "7") }
            )
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "8",
                onClick = { if (code.length < 6) onCode(code + "8") }
            )
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "9",
                onClick = { if (code.length < 6) onCode(code + "9") }
            )
        }
        Row(
            Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .height(54.dp)
                    .weight(1f)
                    .clickable(
                        onClick = {
                            if (code.isNotEmpty()) {
                                onCode(
                                    code.substring(0, code.length - 1)
                                )
                            }
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
                    contentDescription = "Back"
                )
            }
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "0",
                onClick = { if (code.length < 6) onCode(code + "0") }
            )

            Column(
                modifier = Modifier
                    .height(54.dp)
                    .weight(1f)
                    .clickable(
                        onClick = {
                            if (code.isNotEmpty()) {
                                onCode("")
                            }
                        },
                        indication = ripple(color = MaterialTheme.colorScheme.primary),
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    modifier = Modifier.size(38.dp),
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete"
                )
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    modifier: Modifier,
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier.height(54.dp),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(.8f)
        )
    }
}
