package com.m3u.features.favorite

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.features.favorite.vo.LiveDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class FavouriteViewModel @Inject constructor(
    liveRepository: LiveRepository,
    feedRepository: FeedRepository,
    application: Application
) : BaseViewModel<FavoriteState, FavoriteEvent>(
    application = application,
    emptyState = FavoriteState()
) {

    init {
        liveRepository
            .observeAll()
            .map { lives ->
                lives.filter { it.favourite }
            }
            .onEach { lives ->
                writable.update { state ->
                    state.copy(
                        lives = lives.map {
                            val feed = feedRepository.get(it.feedUrl)
                            LiveDetail(
                                live = it,
                                title = feed?.title.orEmpty()
                            )
                        }
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: FavoriteEvent) {

    }
}