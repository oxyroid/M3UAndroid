package com.m3u.androidApp.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.data.database.entity.Post
import com.m3u.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    application: Application,
    private val postRepository: PostRepository,
) : BaseViewModel<RootState, RootEvent>(
    application = application,
    emptyState = RootState()
) {
    init {
        fetchPosts()
    }

    val posts = postRepository
        .observeUnreadPosts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )


    override fun onEvent(event: RootEvent) {
        when (event) {
            is RootEvent.OnPost -> onPost(event.post)
            RootEvent.OnNext -> onNext()
            RootEvent.OnPrevious -> onPrevious()
            RootEvent.OnRead -> onRead()
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
        val post = readable.post?: return
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
}

data class RootState(
    val post: Post? = null,
)

sealed class RootEvent {
    data class OnPost(val post: Post?) : RootEvent()
    object OnNext : RootEvent()
    object OnPrevious : RootEvent()
    object OnRead : RootEvent()
}