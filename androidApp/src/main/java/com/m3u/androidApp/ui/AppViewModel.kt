package com.m3u.androidApp.ui

import androidx.lifecycle.ViewModel
import com.m3u.data.service.DynamicMessageService
import com.m3u.ui.Action
import com.m3u.ui.Fob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    dynamicMessageService: DynamicMessageService
) : ViewModel() {
    val message = dynamicMessageService.message

    val title: MutableStateFlow<String> = MutableStateFlow("")
    val actions: MutableStateFlow<List<Action>> = MutableStateFlow(emptyList())
    val fob: MutableStateFlow<Fob?> = MutableStateFlow(null)
}

