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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * PIN input screen for Android TV with D-pad navigation and numeric keypad
 * Enforces exactly 6 digits with enterprise-grade TV UX
 */
@Composable
fun PINInputScreen(
    title: String,
    subtitle: String? = null,
    errorMessage: String? = null,
    onPINEntered: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

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

                                // Auto-proceed when 6 digits entered
                                if (pin.length == 6) {
                                    if (!showConfirm) {
                                        confirmPin = pin
                                        showConfirm = true
                                        pin = ""
                                    } else {
                                        if (confirmPin == pin) {
                                            onPINEntered(pin)
                                        } else {
                                            localError = "PINs do not match"
                                            showConfirm = false
                                            pin = ""
                                            confirmPin = ""
                                        }
                                    }
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
                                    if (!showConfirm) {
                                        confirmPin = pin
                                        showConfirm = true
                                        pin = ""
                                    } else {
                                        if (confirmPin == pin) {
                                            onPINEntered(pin)
                                        } else {
                                            localError = "PINs do not match"
                                            showConfirm = false
                                            pin = ""
                                            confirmPin = ""
                                        }
                                    }
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
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.width(600.dp)
        ) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Subtitle
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PIN indicator dots
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

            // Status text
            Text(
                text = if (!showConfirm) "Enter 6-digit PIN" else "Confirm PIN",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Error message
            val displayError = localError ?: errorMessage
            if (displayError != null) {
                Text(
                    text = displayError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Numeric keypad with D-pad navigation
            NumericKeypad(
                onDigitClick = { digit ->
                    if (pin.length < 6) {
                        pin += digit
                        localError = null

                        if (pin.length == 6) {
                            if (!showConfirm) {
                                confirmPin = pin
                                showConfirm = true
                                pin = ""
                            } else {
                                if (confirmPin == pin) {
                                    onPINEntered(pin)
                                } else {
                                    localError = "PINs do not match"
                                    showConfirm = false
                                    pin = ""
                                    confirmPin = ""
                                }
                            }
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

            Spacer(modifier = Modifier.height(16.dp))

            // Cancel button
            onCancel?.let { cancelAction ->
                Button(
                    onClick = cancelAction
                ) {
                    Text("Cancel")
                }
            }

            // Instructions
            Text(
                text = "Use D-pad or number keys (0-9) to enter PIN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * TV-optimized numeric keypad with D-pad navigation
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

/**
 * Simplified PIN unlock screen (no confirmation needed)
 */
@Composable
fun PINUnlockScreen(
    errorMessage: String? = null,
    onPINEntered: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }

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
                                if (pin.length == 6) {
                                    onPINEntered(pin)
                                    pin = "" // Clear for retry if wrong
                                }
                            }
                            true
                        }
                        // Handle numpad keys
                        keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                            val digit = (keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString()
                            if (pin.length < 6) {
                                pin += digit
                                if (pin.length == 6) {
                                    onPINEntered(pin)
                                    pin = "" // Clear for retry if wrong
                                }
                            }
                            true
                        }
                        // Backspace to delete last digit
                        keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_BACK -> {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.width(600.dp)
        ) {
            // Title
            Text(
                text = "Enter PIN to Unlock",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PIN indicator dots
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
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Numeric keypad with D-pad navigation
            NumericKeypad(
                onDigitClick = { digit ->
                    if (pin.length < 6) {
                        pin += digit
                        if (pin.length == 6) {
                            onPINEntered(pin)
                            pin = "" // Clear for retry if wrong
                        }
                    }
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                    }
                },
                onClearClick = {
                    pin = ""
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Cancel button
            onCancel?.let { cancelAction ->
                Button(
                    onClick = cancelAction
                ) {
                    Text("Cancel")
                }
            }

            // Instructions
            Text(
                text = "Use D-pad or number keys (0-9) to enter PIN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
