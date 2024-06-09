package com.m3u.feature.crash.screen.detail

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.m3u.core.architecture.FileProvider
import com.m3u.data.repository.media.MediaRepository
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
        file = fileProvider.read(path) ?: return
        text = file?.readText().orEmpty()
    }

    internal fun save(uri: Uri) {
        mediaRepository.openOutputStream(uri)?.use { output ->
            val bytes = file?.readBytes() ?: ByteArray(0)
            output.write(bytes)
        }
    }

    var text: String by mutableStateOf("")
}