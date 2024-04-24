package com.m3u.features.crash

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.RemoteDirectionService
import com.m3u.ui.EventBus.registerActionEventCollector
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.AbstractHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

internal typealias CreateDocumentCallback = (Uri?) -> Unit

@AndroidEntryPoint
class CrashActivity : ComponentActivity() {
    private val helper by lazy {
        AbstractHelper(
            activity = this,
            mainDispatcher = mainDispatcher,
            messager = messager,
            playerManager = playerManager
        )
    }

    @Inject
    lateinit var preferences: Preferences
    private lateinit var launcher: ActivityResultLauncher<String>

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    @Dispatcher(Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var messager: Messager

    @Inject
    lateinit var remoteDirectionService: RemoteDirectionService

    override fun onCreate(savedInstanceState: Bundle?) {
        INSTANCE = this
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launcher = registerForActivityResult(CreateDocument("text/*")) { uri ->
            val iterator = callbacks.iterator()
            while (iterator.hasNext()) {
                val callback = iterator.next()
                callback.invoke(uri)
                iterator.remove()
            }
        }
        setContent {
            Toolkit(
                preferences = preferences,
                helper = helper
            ) {
                CrashApp()
            }
        }
        registerActionEventCollector(remoteDirectionService.actions)
    }

    private val callbacks = mutableListOf<CreateDocumentCallback>()
    private fun addCallback(callback: CreateDocumentCallback) {
        callbacks += callback
    }

    override fun onDestroy() {
        super.onDestroy()
        INSTANCE = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    companion object {
        private var INSTANCE: CrashActivity? = null
        internal fun createDocument(extraTitle: String, callback: CreateDocumentCallback) {
            INSTANCE?.addCallback(callback)
            INSTANCE?.launcher?.launch(extraTitle)
        }
    }
}