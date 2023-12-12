package com.m3u.androidApp.ui

import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.EmptyMessage
import com.m3u.data.service.UiService
import com.m3u.ui.Action
import com.m3u.ui.Fob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    uiService: UiService
) : BaseViewModel<Any, Unit, EmptyMessage>(
    emptyState = Any()
) {
    val snacker = uiService.snacker

    val title: MutableStateFlow<String> = MutableStateFlow("")
    val actions: MutableStateFlow<List<Action>> = MutableStateFlow(emptyList())
    val fob: MutableStateFlow<Fob?> = MutableStateFlow(null)

    override fun onEvent(event: Unit) {

    }
}

