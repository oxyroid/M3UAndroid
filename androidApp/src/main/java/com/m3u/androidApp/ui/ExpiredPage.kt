package com.m3u.androidApp.ui

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.system.exitProcess

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ExpiredPage(message: String, androidId: String) {
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Check if width is larger than height (landscape mode)
            val isLandscape = maxWidth > maxHeight

            val horizontalModifier = if (isLandscape) Modifier.fillMaxWidth(0.7f) else Modifier.fillMaxWidth()

            Column(
                modifier = Modifier
                    .then(horizontalModifier)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Display the Android ID prominently
                Text(
                    text = "ИД: $androidId",
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Centered message text
                Text(
                    text = message,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Reload button
                Button(onClick = {
                    // Restart the app
                    activity?.let {
                        it.finish()
                        it.startActivity(it.intent)
                        exitProcess(0) // Optionally close the app completely
                    }
                }) {
                    Text("Қайта уриниш")
                }
            }
        }
    }
}
