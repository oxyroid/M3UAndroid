package com.m3u.material.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.substring
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CodeSkeleton(
    code: String,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    keyboard: Boolean = false,
    onKeyboard: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Enter Code From Television",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Make sure connect to the same Wi-Fi",
                modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.78f)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp, 16.dp, 32.dp, 16.dp)
                    .clickable(
                        onClick = onKeyboard,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CodeField(
                    text = if (code.isNotEmpty()) {
                        code.substring(TextRange(0, 1))
                    } else {
                        ""
                    }
                )

                CodeField(
                    text = if (code.length > 1) {
                        code.substring(TextRange(1, 2))
                    } else {
                        ""
                    }
                )

                CodeField(
                    text = if (code.length > 2) {
                        code.substring(TextRange(2, 3))
                    } else {
                        ""
                    }
                )

                CodeField(
                    text = if (code.length > 3) {
                        code.substring(TextRange(3, 4))
                    } else {
                        ""
                    }
                )

                CodeField(
                    text = if (code.length > 4) {
                        code.substring(TextRange(4, 5))
                    } else {
                        ""
                    }
                )

                CodeField(
                    text = if (code.length > 5) {
                        code.substring(TextRange(5, 6))
                    } else {
                        ""
                    }
                )
            }

            TextButton(
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                enabled = !loading && code.length == 6,
                onClick = {
                    onSubmit()
                },
            ) {
                Text(if (loading) "CONNECTING" else "CONNECT")
            }
        }

        AnimatedVisibility(
            visible = keyboard,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut()
        ) {
            VirtualNumberKeyboard(
                code = code,
                onCode = onCode,
            )
        }
    }
}

@Composable
fun VirtualNumberKeyboard(
    modifier: Modifier = Modifier,
    code: String,
    onCode: (String) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .background(MaterialTheme.colorScheme.onSurface.copy(.1f))
            .padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues())
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
                        indication = rememberRipple(color = MaterialTheme.colorScheme.primary),
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    modifier = Modifier.size(38.dp),
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Delete"
                )
            }
            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = "0",
                onClick = { if (code.length < 6) onCode(code + "0") }
            )

            KeyboardKey(
                modifier = Modifier.weight(1f),
                text = ":",
                onClick = { if (code.length < 6) onCode("$code:") }
            )
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
        shape = RoundedCornerShape(0.dp),
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

@Composable
private fun CodeField(text: String) {
    Box(
        Modifier
            .padding(start = 4.dp, end = 4.dp)
            .size(40.dp, 45.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(.05f),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(.1f)),
                RoundedCornerShape(6.dp)
            )
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (text.isBlank()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 13.dp)
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(.15f))
            )
        }
    }
}
