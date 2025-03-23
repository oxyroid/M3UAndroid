package com.m3u.extension

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.extension.RemoteClient
import com.m3u.extension.ui.theme.M3UTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val client = RemoteClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            M3UTheme {
                val coroutineScope = rememberCoroutineScope()
                val isConnected by client.isConnectedObservable.collectAsStateWithLifecycle(false)
                var channelCount by remember { mutableIntStateOf(-1) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        Button(
                            enabled = !isConnected,
                            onClick = {
                                client.connect(this@MainActivity)
                            }
                        ) {
                            Text(
                                text = if (isConnected) "Connected" else "Connect"
                            )
                        }
                        Button(
                            enabled = isConnected,
                            onClick = {
                                coroutineScope.launch {
                                    channelCount = client.call("read-channel-count", "{}")
                                        ?.toIntOrNull() ?: -1
                                }
                            }
                        ) {
                            Text(
                                text = "Read Channel Count($channelCount)"
                            )
                        }
                    }
                }
            }
        }
    }
}
