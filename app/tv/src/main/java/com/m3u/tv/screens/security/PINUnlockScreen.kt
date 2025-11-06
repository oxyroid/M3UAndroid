package com.m3u.tv.screens.security

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import timber.log.Timber

/**
 * Full-screen PIN unlock overlay shown on app startup when PIN encryption is enabled.
 * Blocks all app access until correct PIN is entered.
 */
@Composable
fun PINUnlockScreen(
    onPINEntered: (String) -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf(errorMessage) }

    // Handle hardware keyboard input for emulator/testing
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    when {
                        // Handle number keys (0-9)
                        keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                            val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
                            if (pin.length < 6) {
                                pin += digit
                                localError = null

                                // Auto-submit when 6 digits entered
                                if (pin.length == 6) {
                                    Timber.tag("PINUnlockScreen").d("6 digits entered, submitting...")
                                    onPINEntered(pin)
                                    // Don't clear PIN here - let the parent handle success/failure
                                }
                            }
                            true
                        }
                        // Handle numpad keys
                        keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                            val digit = (keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString()
                            if (pin.length < 6) {
                                pin += digit
                                localError = null

                                if (pin.length == 6) {
                                    Timber.tag("PINUnlockScreen").d("6 digits entered via numpad, submitting...")
                                    onPINEntered(pin)
                                }
                            }
                            true
                        }
                        // Backspace to delete last digit
                        keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_BACK -> {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                                localError = null
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title
            Text(
                text = "Enter PIN to Unlock",
                style = MaterialTheme.typography.displaySmall
            )

            // Subtitle
            Text(
                text = "Database is encrypted",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PIN indicator dots (6 circles)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pin.length)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            // Error message
            if (localError != null) {
                Text(
                    text = localError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Numeric keypad
            NumericKeypad(
                onDigitClick = { digit ->
                    if (pin.length < 6) {
                        pin += digit
                        localError = null

                        // Auto-submit when 6 digits entered
                        if (pin.length == 6) {
                            Timber.tag("PINUnlockScreen").d("6 digits entered via keypad, submitting...")
                            onPINEntered(pin)
                        }
                    }
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                        localError = null
                    }
                },
                onClearClick = {
                    pin = ""
                    localError = null
                }
            )
        }
    }
}

/**
 * Numeric keypad with 3x4 layout optimized for TV D-pad navigation.
 * Layout:
 * 1 2 3
 * 4 5 6
 * 7 8 9
 * CLR 0 ←
 */
@Composable
private fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Rows 1-3 (digits 1-9)
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (col in 0..2) {
                    val digit = (row * 3 + col + 1).toString()
                    Button(
                        onClick = { onDigitClick(digit) },
                        modifier = Modifier.size(width = 80.dp, height = 60.dp)
                    ) {
                        Text(
                            text = digit,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        // Row 4 (Clear, 0, Backspace)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onClearClick,
                modifier = Modifier.size(width = 80.dp, height = 60.dp)
            ) {
                Text(
                    text = "CLR",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Button(
                onClick = { onDigitClick("0") },
                modifier = Modifier.size(width = 80.dp, height = 60.dp)
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Button(
                onClick = onBackspaceClick,
                modifier = Modifier.size(width = 80.dp, height = 60.dp)
            ) {
                Text(
                    text = "←",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
