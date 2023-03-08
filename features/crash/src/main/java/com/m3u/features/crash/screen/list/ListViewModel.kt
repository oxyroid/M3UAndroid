package com.m3u.features.crash.screen.list

import android.app.Application
import com.m3u.core.architecture.BaseViewModel
import com.m3u.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    application: Application,
    mediaRepository: MediaRepository
) : BaseViewModel<ListState, ListEvent>(
    application = application,
    emptyState = ListState()
) {
    init {
        val files = mediaRepository.readAllLogFiles()
        writable.update {
            it.copy(
                logs = files
            )
        }
    }

    override fun onEvent(event: ListEvent) {

    }
}