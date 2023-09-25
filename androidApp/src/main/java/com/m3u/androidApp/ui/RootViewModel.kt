package com.m3u.androidApp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.m3u.androidApp.AppPublisher
import com.m3u.ui.TopLevelDestination
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.service.BannerService
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.eventOf
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.entity.Post
import com.m3u.data.repository.PostRepository
import com.m3u.ui.model.ScaffoldAction
import com.m3u.ui.model.ScaffoldFob
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    application: Application,
    private val postRepository: PostRepository,
    configuration: Configuration,
    private val publisher: AppPublisher,
    bannerService: BannerService
) : BaseViewModel<RootState, RootEvent>(
    application = application,
    emptyState = RootState(
        configuration = configuration
    )
) {
    init {
        if (!readable.silentMode) {
            fetchPosts()
        }
        bannerService
            .messages
            .onEach { message ->
                appendTemporalPost(
                    Post.createTemporal(message)
                )
            }
            .launchIn(viewModelScope)

        initialState()
    }

    val posts = postRepository
        .observeActivePosts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val childTitle: MutableStateFlow<String> = MutableStateFlow("")
    val actions: MutableStateFlow<List<ScaffoldAction>> = MutableStateFlow(emptyList())
    val fab: MutableStateFlow<ScaffoldFob?> = MutableStateFlow(null)

    override fun onEvent(event: RootEvent) {
        when (event) {
            is RootEvent.OnPost -> onPost(event.post)
            RootEvent.OnNext -> onNext()
            RootEvent.OnPrevious -> onPrevious()
            RootEvent.OnRead -> onRead()
        }
    }

    private fun appendTemporalPost(post: Post) {
        check(post.temporal) { "post must be temporal" }
        viewModelScope.launch {
            postRepository.temporal(post)
        }
    }

    private fun onPost(post: Post?) {
        writable.update {
            it.copy(
                post = post
            )
        }
    }

    private fun onNext() {
        onMove(true)
    }

    private fun onPrevious() {
        onMove(false)
    }

    private fun onMove(next: Boolean) {
        val post = readable.post
        val posts = posts.value
        val index = posts.indexOf(post)
        if (index == -1) return
        if (next && index == posts.lastIndex) return
        if (!next && index == 0) return
        val targetIndex = if (next) index + 1 else index - 1
        writable.update {
            it.copy(
                post = posts[targetIndex]
            )
        }
    }

    private fun onRead() {
        val post = readable.post ?: return
        val posts = posts.value
        val index = posts.indexOf(post)
        if (index == -1) return
        viewModelScope.launch {
            postRepository.read(post.id)
        }
    }

    private fun fetchPosts() {
        viewModelScope.launch {
            postRepository.fetchAll()
        }
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
    val post: Post? = null,
    val navigateTopLevelDestination: Event<TopLevelDestination> = handledEvent(),
    private val configuration: Configuration
) {
    var cinemaMode: Boolean by configuration.cinemaMode
    var initialTabIndex: Int by configuration.initialTabIndex
    var silentMode: Boolean by configuration.silentMode
}

sealed class RootEvent {
    data class OnPost(val post: Post?) : RootEvent()
    data object OnNext : RootEvent()
    data object OnPrevious : RootEvent()
    data object OnRead : RootEvent()
}