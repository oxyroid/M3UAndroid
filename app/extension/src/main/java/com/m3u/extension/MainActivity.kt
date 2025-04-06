package com.m3u.extension

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.extension.api.CallTokenConst
import com.m3u.extension.api.RemoteClient
import com.m3u.extension.api.business.InfoApi
import com.m3u.extension.api.business.SubscribeApi
import com.m3u.extension.api.model.AddPlaylistRequest
import com.m3u.extension.ui.theme.M3UTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val client = RemoteClient()
    private val infoApi = client.create<InfoApi>()
    private val subscribeApi = client.create<SubscribeApi>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var callToken by mutableStateOf(handleArguments(intent))
        val commands = mutableStateListOf<String>()

        setContent {
            M3UTheme {
                val coroutineScope = rememberCoroutineScope()
                val isConnected by client.isConnectedObservable.collectAsStateWithLifecycle(false)
                DisposableEffect(Unit) {
                    onDispose {
                        if (isConnected) {
                            client.disconnect(this@MainActivity)
                        }
                    }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        val lazyListState = rememberLazyListState()
                        LaunchedEffect(commands.size) {
                            lazyListState.scrollToItem(Int.MAX_VALUE)
                        }
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                                .weight(1f),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            itemsIndexed(commands) { index, command ->
                                val isFocused = index == commands.lastIndex
                                val fontWeight by animateIntAsState(if (isFocused) 700 else 400)
                                val color by animateColorAsState(if (isFocused) Color.Yellow else Color.White)

                                Text(
                                    text = "> $command",
                                    fontFamily = FontFamily.Monospace,
                                    color = color,
                                    fontWeight = FontWeight(fontWeight),
                                    modifier = Modifier.weight(1f),
                                    style = LocalTextStyle.current.copy(
                                        lineBreak = LineBreak.Paragraph
                                    )
                                )
                            }
                        }
                        Button(
                            enabled = callToken != null,
                            onClick = {
                                val callToken = callToken
                                if (!isConnected && callToken != null) {
                                    client.connect(
                                        context = this@MainActivity,
                                        targetPackageName = callToken.packageName,
                                        targetClassName = callToken.className,
                                        targetPermission = callToken.permission,
                                        accessKey = callToken.accessKey
                                    )
                                } else {
                                    client.disconnect(this@MainActivity)
                                }
                            }
                        ) {
                            Text(
                                text = when {
                                    callToken == null -> "No CallToken"
                                    isConnected -> "Disconnect"
                                    else -> "Connect"
                                }
                            )
                        }
                        Button(
                            enabled = isConnected,
                            onClick = {
                                coroutineScope.launch {
                                    commands += infoApi.getAppInfo().toString()
                                }
                            }
                        ) {
                            Text(
                                text = "GetAppInfo"
                            )
                        }
                        Button(
                            enabled = isConnected,
                            onClick = {
                                coroutineScope.launch {
                                    commands += infoApi.getModules().toString()
                                }
                            }
                        ) {
                            Text(
                                text = "GetModulesResponse"
                            )
                        }
                        Button(
                            enabled = isConnected,
                            onClick = {
                                coroutineScope.launch {
                                    commands += subscribeApi.addPlaylist(
                                        AddPlaylistRequest(
                                            url = "https://example.com/playlist.m3u?time=${System.currentTimeMillis()}",
                                            title = "Test Playlist ${System.currentTimeMillis()}",
                                            user_agent = "Test User Agent"
                                        )
                                    ).toString()
                                }
                            }
                        ) {
                            Text(
                                text = "AddPlaylist"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleArguments(intent: Intent): CallToken? {
        val packageName = intent.getStringExtra(CallTokenConst.PACKAGE_NAME) ?: return null
        val className = intent.getStringExtra(CallTokenConst.CLASS_NAME) ?: return null
        val permission = intent.getStringExtra(CallTokenConst.PERMISSION) ?: return null
        val accessKey = intent.getStringExtra(CallTokenConst.ACCESS_KEY) ?: return null
        return CallToken(packageName, className, permission, accessKey)
    }
}

private data class CallToken(
    val packageName: String,
    val className: String,
    val permission: String,
    val accessKey: String
)