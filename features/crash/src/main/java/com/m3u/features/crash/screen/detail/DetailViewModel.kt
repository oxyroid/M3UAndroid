package com.m3u.features.crash.screen.detail

import com.m3u.core.architecture.FilePath
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.Message
import com.m3u.data.io.CrashFilePathCacher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val cacher: CrashFilePathCacher
) : BaseViewModel<DetailState, DetailEvent, Message.Static>(
    emptyState = DetailState()
) {
    override fun onEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.Init -> init(event.path)
        }
    }

    private fun init(path: String) {
        val filePath = FilePath(path)
        val file = cacher.read(filePath) ?: return
        val text = file.readText()
        writable.update {
            it.copy(
                text = text
            )
        }
    }
}