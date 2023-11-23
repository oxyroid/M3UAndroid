package com.m3u.features.crash.screen.list

import com.m3u.core.architecture.FilePathCacher
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.EmptyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    cacher: FilePathCacher
) : BaseViewModel<ListState, ListEvent, EmptyMessage>(
    emptyState = ListState()
) {
    init {
        val files = cacher.readAll()
        writable.update {
            it.copy(
                logs = files
            )
        }
    }

    override fun onEvent(event: ListEvent) {

    }
}
