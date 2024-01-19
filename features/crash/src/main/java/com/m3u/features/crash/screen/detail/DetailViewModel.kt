package com.m3u.features.crash.screen.detail

import com.m3u.core.architecture.TraceFileProvider
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.data.repository.MediaRepository
import com.m3u.features.crash.CrashActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val provider: TraceFileProvider,
    private val mediaRepository: MediaRepository
) : BaseViewModel<DetailState, DetailEvent>(
    emptyState = DetailState()
) {
    override fun onEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.Init -> init(event.path)
            DetailEvent.Save -> save()
        }
    }

    private var file: File? = null
    private fun init(path: String) {
        file = provider.read(path) ?: return
        val text = file?.readText().orEmpty()
        writable.update {
            it.copy(
                text = text
            )
        }
    }

    private fun save() {
        val currentFile = file ?: return
        CrashActivity.createDocument(currentFile.nameWithoutExtension) { uri ->
            uri ?: return@createDocument
            mediaRepository.openOutputStream(uri)?.use { output ->
                val bytes = currentFile.readBytes()
                output.write(bytes)
            }
        }
    }
}