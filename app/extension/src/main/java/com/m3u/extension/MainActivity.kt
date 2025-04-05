package com.m3u.extension

import android.content.Intent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.extension.api.CallTokenConst
import com.m3u.extension.api.RemoteClient
import com.m3u.extension.api.business.InfoApi
import com.m3u.extension.api.model.GetAppInfoResponse
import com.m3u.extension.api.model.GetMethodsRequest
import com.m3u.extension.api.model.GetMethodsResponse
import com.m3u.extension.api.model.GetModulesResponse
import com.m3u.extension.ui.theme.M3UTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val client = RemoteClient()
    private val infoApi = client.create<InfoApi>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var callToken by mutableStateOf(handleArguments(intent))
        var getAppInfo: GetAppInfoResponse? by mutableStateOf(null)
        var getModulesResponse: GetModulesResponse? by mutableStateOf(null)
        var getMethodsResponse: GetMethodsResponse? by mutableStateOf(null)

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
                                    getAppInfo = infoApi.getAppInfo()
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
                                    getModulesResponse = infoApi.getModules()
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
                                    getMethodsResponse = infoApi.getMethods(
                                        GetMethodsRequest(
                                            module = getModulesResponse?.modules?.firstOrNull().orEmpty()
                                        )
                                    )
                                }
                            }
                        ) {
                            Text(
                                text = "GetMethodsResponse"
                            )
                        }
                        Text(
                            text = getAppInfo?.toString().orEmpty()
                        )
                        Text(
                            text = getModulesResponse?.toString().orEmpty()
                        )
                        Text(
                            text = getMethodsResponse?.toString().orEmpty()
                        )
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