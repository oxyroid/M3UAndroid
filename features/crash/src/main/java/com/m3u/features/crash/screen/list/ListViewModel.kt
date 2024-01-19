package com.m3u.features.crash.screen.list

import com.m3u.core.architecture.TraceFileProvider
import com.m3u.core.architecture.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    provider: TraceFileProvider
) : BaseViewModel<ListState, ListEvent>(
    emptyState = ListState()
) {
    init {
        val files = provider.readAll()
        writable.update {
            it.copy(
                logs = files
            )
        }
    }

    override fun onEvent(event: ListEvent) {

    }
}
