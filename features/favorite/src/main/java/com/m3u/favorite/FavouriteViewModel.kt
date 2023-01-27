package com.m3u.favorite

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.m3u.core.BaseViewModel
import com.m3u.core.util.createClazzKey
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.favorite.vo.LiveWithTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class FavouriteViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    liveRepository: LiveRepository,
    subscriptionRepository: SubscriptionRepository
) : BaseViewModel<FavoriteState, FavoriteEvent>(
    application = application,
    emptyState = FavoriteState(),
    savedStateHandle = savedStateHandle,
    key = createClazzKey<FavouriteViewModel>()
) {

    init {
        liveRepository
            .observeAllLives()
            .map { lives ->
                lives.filter { it.favourite }
            }
            .onEach { lives ->
                writable.update { state ->
                    state.copy(
                        lives = lives.map {
                            val subscription = subscriptionRepository.getDetail(it.subscriptionUrl)
                            LiveWithTitle(
                                live = it,
                                title = subscription?.title.orEmpty()
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