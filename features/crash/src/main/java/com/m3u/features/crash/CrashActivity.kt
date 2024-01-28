package com.m3u.features.crash

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import com.m3u.core.architecture.pref.Pref
import com.m3u.ui.Toolkit
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

internal typealias CreateDocumentCallback = (Uri?) -> Unit

@AndroidEntryPoint
class CrashActivity : ComponentActivity() {
    @Inject
    lateinit var pref: Pref
    private lateinit var launcher: ActivityResultLauncher<String>
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
            Toolkit(pref = pref) {
                CrashApp()
            }
        }
    }

    private val callbacks = mutableListOf<CreateDocumentCallback>()
    private fun addCallback(callback: CreateDocumentCallback) {
        callbacks += callback
    }

    override fun onDestroy() {
        super.onDestroy()
        INSTANCE = null
    }

    companion object {
        private var INSTANCE: CrashActivity? = null
        internal fun createDocument(extraTitle: String, callback: CreateDocumentCallback) {
            INSTANCE?.addCallback(callback)
            INSTANCE?.launcher?.launch(extraTitle)
        }
    }
}