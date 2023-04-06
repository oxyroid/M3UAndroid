package com.m3u.features.crash.screen.list

import android.app.Application
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.architecture.reader.FileReader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.update

@HiltViewModel
class ListViewModel @Inject constructor(
    application: Application,
    reader: FileReader
) : BaseViewModel<ListState, ListEvent>(
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