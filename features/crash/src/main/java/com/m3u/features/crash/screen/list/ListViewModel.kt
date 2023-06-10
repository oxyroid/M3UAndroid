package com.m3u.features.crash.screen.list

import android.app.Application
import com.m3u.core.architecture.viewmodel.AndroidPlatformViewModel
import com.m3u.core.architecture.reader.FileReader
import kotlinx.coroutines.flow.update

class ListViewModel(
    application: Application,
    reader: FileReader
) : AndroidPlatformViewModel<ListState, ListEvent>(
    application = application,
    emptyState = ListState()
) {
    init {
        val files = reader.read()
        writable.update {
            it.copy(
                logs = files
            )
        }
    }

    override fun onEvent(event: ListEvent) {

    }
}