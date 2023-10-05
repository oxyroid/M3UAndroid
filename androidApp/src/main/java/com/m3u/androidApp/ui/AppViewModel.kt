package com.m3u.androidApp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.androidApp.AppPublisher
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.service.UiService
import com.m3u.ui.TopLevelDestination
import com.m3u.ui.model.Action
import com.m3u.ui.model.Fob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    application: Application,
    configuration: Configuration,
    private val publisher: AppPublisher,
    uiService: UiService
) : BaseViewModel<RootState, Unit>(
    application = application,
    emptyState = RootState(
        configuration = configuration
    )
) {
    init {
        initialState()
    }

    val snacker = uiService.snacker

    val title: MutableStateFlow<String> = MutableStateFlow("")
    val actions: MutableStateFlow<List<Action>> = MutableStateFlow(emptyList())
    val fob: MutableStateFlow<Fob?> = MutableStateFlow(null)

    override fun onEvent(event: Unit) {

    }

    private fun initialState() {
        val index = getSafelyInitialTabIndex()
        val destination = TopLevelDestination.values()[index]
        writable.update {
            it.copy(
                navigateTopLevelDestination = eventOf(destination)
            )
        }
    }

    private fun getSafelyInitialTabIndex(): Int {
        val index = readable.initialTabIndex
        if (index < 0 || index > publisher.destinationsCount - 1) return 0
        return index
    }
}

data class RootState(
    val navigateTopLevelDestination: Event<TopLevelDestination> = handledEvent(),
    private val configuration: Configuration
) {
    var cinemaMode: Boolean by configuration.cinemaMode
    var initialTabIndex: Int by configuration.initialTabIndex
}
