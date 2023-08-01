package com.m3u.features.crash.screen.detail

import android.app.Application
import com.m3u.core.architecture.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
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