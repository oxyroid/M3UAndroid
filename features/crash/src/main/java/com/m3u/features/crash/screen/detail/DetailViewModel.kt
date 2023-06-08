package com.m3u.features.crash.screen.detail

import android.app.Application
import com.m3u.core.architecture.BaseViewModel
import java.io.File
import kotlinx.coroutines.flow.update

class DetailViewModel(
    application: Application
) : BaseViewModel<DetailState, DetailEvent>(
    application = application,
    emptyState = DetailState()
) {
    override fun onEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.Init -> init(event.path)
        }
    }

    private fun init(path: String) {
        val file = File(context.cacheDir, path)
        val text = file.readText()
        writable.update {
            it.copy(
                text = text
            )
        }
    }
}