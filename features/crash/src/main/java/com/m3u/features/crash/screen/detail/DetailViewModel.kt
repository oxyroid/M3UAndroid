package com.m3u.features.crash.screen.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.m3u.core.architecture.FileProvider
import com.m3u.data.repository.MediaRepository
import com.m3u.features.crash.CrashActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val fileProvider: FileProvider,
    private val mediaRepository: MediaRepository
) : ViewModel() {
    private var file: File? = null
    internal fun init(path: String) {
        this.file = fileProvider.read(path) ?: return
        this.text = file?.readText().orEmpty()
    }

    internal fun save() {
        val currentFile = file ?: return
        CrashActivity.createDocument(currentFile.nameWithoutExtension) { uri ->
            uri ?: return@createDocument
            mediaRepository.openOutputStream(uri)?.use { output ->
                val bytes = currentFile.readBytes()
                output.write(bytes)
            }
        }
    }

    var text: String by mutableStateOf("")
}